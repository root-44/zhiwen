package com.zhiwen.service.embedding;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiwen.entity.Document;
import com.zhiwen.entity.DocumentChunk;
import com.zhiwen.mapper.DocumentChunkMapper;
import com.zhiwen.mapper.DocumentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Embedding 服务:把文本切片批量向量化并写入 PgVector
 *
 * <p>设计要点(面试讲点):
 * <ul>
 *   <li>批量调用:Embedding API 按 token 计费且每次请求有网络开销,16 片一批摊薄 RTT</li>
 *   <li>不用长事务包住 API 调用:HTTP 调用可能数秒,长事务占连接;改为每批成功后立刻回填状态</li>
 *   <li>向量与元数据分离:向量本体在 vector_store,业务元数据在 kb_chunk,vector_id 双向关联,
 *       既能语义检索,又能回表拿原文/页码做引用溯源</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private final VectorStore vectorStore;
    private final DocumentChunkMapper chunkMapper;
    private final DocumentMapper documentMapper;

    /** 每批切片数:bge 接口单请求 32 条以内安全,取 16 保守 */
    private static final int BATCH_SIZE = 16;

    /**
     * 对文档下所有 PENDING 切片做向量化
     *
     * @return 本次成功入库的切片数
     */
    public int embedDocument(Long documentId) {
        Document doc = documentMapper.selectById(documentId);
        if (doc == null) {
            throw new IllegalArgumentException("文档不存在: " + documentId);
        }

        List<DocumentChunk> pending = chunkMapper.selectList(
                new LambdaQueryWrapper<DocumentChunk>()
                        .eq(DocumentChunk::getDocumentId, documentId)
                        .eq(DocumentChunk::getEmbeddingStatus, "PENDING")
                        .orderByAsc(DocumentChunk::getSeq));
        if (pending.isEmpty()) {
            log.info("文档 {} 无待向量化切片", documentId);
            return 0;
        }

        int success = 0;
        for (int from = 0; from < pending.size(); from += BATCH_SIZE) {
            int to = Math.min(from + BATCH_SIZE, pending.size());
            List<DocumentChunk> batch = pending.subList(from, to);

            // 1. 切片 → Spring AI Document(先分配 UUID,成功后回填业务表)
            List<org.springframework.ai.document.Document> aiDocs = new ArrayList<>(batch.size());
            for (DocumentChunk chunk : batch) {
                String vectorId = UUID.randomUUID().toString();
                chunk.setVectorId(vectorId);   // 暂存到内存对象,API 成功后落库
                aiDocs.add(new org.springframework.ai.document.Document(
                        vectorId, chunk.getContent(), buildMetadata(doc, chunk)));
            }

            // 2. 调 Embedding 模型 + 写 PgVector(Spring AI 内部一次批量 embed)
            vectorStore.add(aiDocs);

            // 3. 回填业务表状态
            for (DocumentChunk chunk : batch) {
                chunk.setEmbeddingStatus("DONE");
                chunkMapper.updateById(chunk);
            }
            success += batch.size();
            log.info("文档 {} 向量化进度 {}/{}", documentId, success, pending.size());
        }
        return success;
    }

    /**
     * 向量元数据:写入 vector_store.metadata(jsonb),检索结果原样带回,用于引用溯源
     * 注意:这些字段不会参与向量化(Embedding 只用 text),只随检索结果返回
     */
    private Map<String, Object> buildMetadata(Document doc, DocumentChunk chunk) {
        Map<String, Object> meta = new HashMap<>();
        meta.put("documentId", doc.getId());
        meta.put("chunkId", chunk.getId());
        meta.put("title", doc.getTitle());
        meta.put("seq", chunk.getSeq());
        meta.put("fileType", doc.getFileType());
        if (chunk.getPageNo() != null) {
            meta.put("pageNo", chunk.getPageNo());
        }
        return meta;
    }
}
