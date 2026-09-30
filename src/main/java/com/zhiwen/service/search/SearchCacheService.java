package com.zhiwen.service.search;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiwen.config.RagProperties;
import com.zhiwen.dto.SearchHit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 语义检索结果 Redis 缓存
 *
 * <p>为什么能缓存:向量检索是无状态的——同一个问题 + 同一批入库文档,结果确定。
 * 热点问题(如"稻瘟病怎么防治")第二次提问直接读 Redis,跳过一次 Embedding API 调用
 * (约 100-300ms)和一次 PgVector 查询。
 *
 * <p>Key 设计:对"归一化问题文本"取 MD5。归一化 = 去首尾空白 + 转小写 + 去掉标点和多余空白,
 * 使"水稻 穗颈瘟?"和"水稻穗颈瘟"命中同一缓存。
 *
 * <p>两个边界:
 * <ul>
 *   <li>只缓存"默认 topK/阈值"的检索;带自定义参数的调参接口不走缓存,避免结果串用</li>
 *   <li>空结果用更短 TTL 缓存:防缓存穿透(恶意用无关问题刷 Embedding),又能在导入新文档后较快恢复</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchCacheService {

    public static final String KEY_PREFIX = "rag:cache:search:";
    private static final TypeReference<List<SearchHit>> LIST_TYPE = new TypeReference<>() {
    };

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final RagProperties props;

    /** 命中返回列表(可能为空列表,表示之前缓存过"无结果");未命中返回 null */
    public List<SearchHit> get(String query) {
        if (!props.isCacheEnabled()) {
            return null;
        }
        String json = redis.opsForValue().get(key(query));
        if (json == null) {
            return null;
        }
        try {
            List<SearchHit> hits = objectMapper.readValue(json, LIST_TYPE);
            log.info("检索缓存命中 q='{}' ({} 片)", query, hits.size());
            return hits;
        } catch (Exception e) {
            // 缓存数据损坏不影响主流程:删掉脏 key,回源检索
            log.warn("检索缓存反序列化失败,删除脏 key: {}", e.getMessage());
            redis.delete(key(query));
            return null;
        }
    }

    public void put(String query, List<SearchHit> hits) {
        if (!props.isCacheEnabled()) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(hits);
            long ttl = hits.isEmpty() ? props.getEmptyCacheTtlSeconds() : props.getCacheTtlSeconds();
            redis.opsForValue().set(key(query), json, java.time.Duration.ofSeconds(ttl));
        } catch (Exception e) {
            // 缓存写失败只记录,不能影响问答主链路
            log.warn("检索缓存写入失败: {}", e.getMessage());
        }
    }

    /**
     * 清空全部检索缓存:文档重新向量化(reindex)/删除后,旧检索结果可能已过时。
     * 用 SCAN 而非 KEYS,避免在大 key 空间下阻塞 Redis 单线程。
     */
    public void evictAll() {
        ScanOptions options = ScanOptions.scanOptions().match(KEY_PREFIX + "*").count(200).build();
        long deleted = 0;
        try (Cursor<String> cursor = redis.scan(options)) {
            while (cursor.hasNext()) {
                redis.delete(cursor.next());
                deleted++;
            }
        } catch (Exception e) {
            log.warn("清空检索缓存失败: {}", e.getMessage());
            return;
        }
        log.info("检索缓存已清空,删除 {} 个 key", deleted);
    }

    private String key(String query) {
        return KEY_PREFIX + DigestUtils.md5DigestAsHex(normalize(query).getBytes(StandardCharsets.UTF_8));
    }

    /** 问题归一化:小写 + 去标点符号 + 折叠空白 */
    private String normalize(String q) {
        return q.trim().toLowerCase()
                .replaceAll("[\\p{P}\\p{S}]+", "")
                .replaceAll("\\s+", "");
    }
}
