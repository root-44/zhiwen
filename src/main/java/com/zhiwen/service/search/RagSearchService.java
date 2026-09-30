package com.zhiwen.service.search;

import com.zhiwen.config.RagProperties;
import com.zhiwen.dto.SearchHit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * RAG 语义检索:问题文本 → 自动 Embedding → PgVector HNSW 余弦近邻搜索 → TopK 切片
 *
 * <p>与"数据库 LIKE 模糊匹配"的本质区别(面试高频):
 * LIKE 匹配字面,问"水稻穗头发病"搜不到写着"穗颈瘟"的段落;
 * Embedding 把文本映射到语义向量空间,"穗头发病"和"穗颈瘟减产"向量距离近,能召回。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagSearchService {

    private final VectorStore vectorStore;
    private final RagProperties props;
    private final SearchCacheService searchCacheService;

    /**
     * 语义检索(默认参数),带 Redis 缓存。
     * 多轮/单轮问答都走这里:热点问题第二次起不再调用 Embedding 接口。
     *
     * @param query 用户问题
     * @return 按相似度从高到低排列的命中切片;无任何结果时返回空列表(上层据此判定"知识库无答案")
     */
    public List<SearchHit> search(String query) {
        List<SearchHit> cached = searchCacheService.get(query);
        if (cached != null) {
            return cached;
        }
        List<SearchHit> hits = search(query, props.getTopK(), props.getSimilarityThreshold());
        searchCacheService.put(query, hits);
        return hits;
    }

    /**
     * 语义检索(可调 topK/阈值,供调参和不同场景复用)
     *
     * @param query     用户问题
     * @param topK      召回数量,null 用配置默认值
     * @param threshold 相似度阈值,null 用配置默认值
     */
    public List<SearchHit> search(String query, Integer topK, Double threshold) {
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(topK == null ? props.getTopK() : topK)
                .similarityThreshold(threshold == null ? props.getSimilarityThreshold() : threshold)
                .build();

        long start = System.currentTimeMillis();
        List<Document> docs = vectorStore.similaritySearch(request);
        long cost = System.currentTimeMillis() - start;

        List<SearchHit> hits = new ArrayList<>();
        if (docs != null) {
            for (Document doc : docs) {
                hits.add(toHit(doc));
            }
        }
        log.info("检索 q='{}' 命中 {}/{} 片,耗时 {}ms",
                query, hits.size(), props.getTopK(), cost);
        return hits;
    }

    /** Spring AI Document → 对外 DTO;metadata 数字类型用 Number 兼容(jsonb 可能返回 Integer) */
    private SearchHit toHit(Document doc) {
        Map<String, Object> meta = doc.getMetadata();
        return new SearchHit(
                asLong(meta.get("chunkId")),
                asLong(meta.get("documentId")),
                asString(meta.get("title")),
                doc.getText(),
                asInt(meta.get("seq")),
                asInt(meta.get("pageNo")),
                doc.getScore()
        );
    }

    private Long asLong(Object v) {
        return v == null ? null : ((Number) v).longValue();
    }

    private Integer asInt(Object v) {
        return v == null ? null : ((Number) v).intValue();
    }

    private String asString(Object v) {
        return v == null ? null : v.toString();
    }
}
