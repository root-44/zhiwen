package com.zhiwen.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiwen.common.BusinessException;
import com.zhiwen.common.ErrorCode;
import com.zhiwen.entity.Document;
import com.zhiwen.entity.DocumentChunk;
import com.zhiwen.mapper.DocumentChunkMapper;
import com.zhiwen.mapper.DocumentMapper;
import com.zhiwen.service.chunk.ChunkService;
import com.zhiwen.service.embedding.EmbeddingService;
import com.zhiwen.service.parser.DocParser;
import com.zhiwen.service.parser.ParserFactory;
import com.zhiwen.service.search.SearchCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * 文档服务:上传 → 解析 → 切片 → 落库 的编排层
 *
 * <p>状态机手动管理而不是整体 @Transactional:
 * 失败时要把 FAILED 状态和原因落库供用户查看,整体回滚会丢失这条记录。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private final ParserFactory parserFactory;
    private final ChunkService chunkService;
    private final EmbeddingService embeddingService;
    private final DocumentMapper documentMapper;
    private final DocumentChunkMapper chunkMapper;
    private final SearchCacheService searchCacheService;

    /** 单文件大小上限 20MB,防大文件拖垮解析线程 */
    private static final long MAX_SIZE = 20L * 1024 * 1024;

    public Document upload(MultipartFile file, String title) {
        validate(file);
        String fileName = file.getOriginalFilename();

        Document doc = new Document();
        doc.setTitle(title != null && !title.isBlank() ? title : stripExt(fileName));
        doc.setFileName(fileName);
        doc.setFileType(extOf(fileName));
        doc.setFileSize(file.getSize());
        doc.setCharCount(0);
        doc.setChunkCount(0);
        doc.setStatus("PARSING");
        documentMapper.insert(doc);

        try {
            // 1. 解析
            DocParser parser = parserFactory.get(fileName);
            List<DocParser.ParsedSection> sections;
            try (InputStream in = file.getInputStream()) {
                sections = parser.parse(fileName, in);
            }
            int charCount = sections.stream().mapToInt(s -> s.text().length()).sum();
            doc.setCharCount(charCount);
            doc.setStatus("CHUNKING");
            documentMapper.updateById(doc);

            // 2. 切片
            List<ChunkService.Chunk> chunks = chunkService.chunk(sections);
            if (chunks.isEmpty()) {
                throw new BusinessException(ErrorCode.DOC_PARSE_FAIL, "切片结果为空");
            }

            // 3. 切片落库(向量入库在 D3 Embedding 阶段做,这里先 PENDING)
            for (int i = 0; i < chunks.size(); i++) {
                ChunkService.Chunk c = chunks.get(i);
                DocumentChunk entity = new DocumentChunk();
                entity.setDocumentId(doc.getId());
                entity.setSeq(i);
                entity.setContent(c.content());
                entity.setCharCount(c.charCount());
                entity.setPageNo(c.pageNo());
                entity.setEmbeddingStatus("PENDING");
                chunkMapper.insert(entity);
            }
            // 4. 向量化入库:切片文本 → Embedding(1024维) → PgVector
            doc.setChunkCount(chunks.size());
            doc.setStatus("EMBEDDING");
            documentMapper.updateById(doc);
            embeddingService.embedDocument(doc.getId());

            doc.setStatus("READY");
            documentMapper.updateById(doc);
            // 知识库内容变化,检索缓存整体失效,避免新文档搜不到/旧结果残留
            searchCacheService.evictAll();
            log.info("文档处理完成: id={}, chunks={}, chars={}", doc.getId(), chunks.size(), charCount);
            return doc;
        } catch (IOException e) {
            return markFailed(doc, "读取上传文件失败: " + e.getMessage());
        } catch (BusinessException e) {
            return markFailed(doc, e.getMessage());
        } catch (Exception e) {
            log.error("文档处理异常 id={}", doc.getId(), e);
            return markFailed(doc, "处理异常: " + e.getClass().getSimpleName());
        }
    }

    public List<Document> listAll() {
        return documentMapper.selectList(
                new LambdaQueryWrapper<Document>().orderByDesc(Document::getCreatedAt));
    }

    public Document getOrThrow(Long id) {
        Document doc = documentMapper.selectById(id);
        if (doc == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "文档不存在: " + id);
        }
        return doc;
    }

    public List<DocumentChunk> chunksOf(Long documentId) {
        getOrThrow(documentId);
        return chunkMapper.selectList(new LambdaQueryWrapper<DocumentChunk>()
                .eq(DocumentChunk::getDocumentId, documentId)
                .orderByAsc(DocumentChunk::getSeq));
    }

    /**
     * 重建向量:对已有文档的 PENDING 切片补做 Embedding
     * 用于 D2 遗留数据、或更换 Embedding 模型后全量重建(重建场景先删 vector_store 再调)
     */
    public Document reindex(Long documentId) {
        Document doc = getOrThrow(documentId);
        doc.setStatus("EMBEDDING");
        doc.setErrorMsg(null);
        documentMapper.updateById(doc);
        try {
            int n = embeddingService.embedDocument(documentId);
            doc.setStatus("READY");
            documentMapper.updateById(doc);
            searchCacheService.evictAll();
            log.info("文档 {} 重建向量完成,本次入库 {} 片", documentId, n);
            return doc;
        } catch (Exception e) {
            log.error("文档 {} 重建向量失败", documentId, e);
            return markFailed(doc, "向量化失败: " + e.getMessage());
        }
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.DOC_EMPTY);
        }
        if (file.getSize() > MAX_SIZE) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "文件超过 20MB 上限");
        }
    }

    private Document markFailed(Document doc, String msg) {
        doc.setStatus("FAILED");
        doc.setErrorMsg(msg);
        documentMapper.updateById(doc);
        return doc;
    }

    private String extOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "unknown" : fileName.substring(dot + 1).toLowerCase();
    }

    private String stripExt(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? fileName : fileName.substring(0, dot);
    }
}
