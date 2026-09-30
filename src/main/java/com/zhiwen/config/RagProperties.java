package com.zhiwen.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RAG 参数配置:与 application.yml 的 zhiwen.rag 前缀绑定
 * 参数外置而非硬编码,压测时调参不用改代码重新编译
 */
@Data
@ConfigurationProperties(prefix = "zhiwen.rag")
public class RagProperties {

    /** 切片目标字符数(中文约 500 字 ≈ 300 token,bge 模型 512 token 上限内) */
    private int chunkSize = 500;

    /** 相邻切片重叠字符数:保证跨切片的语义连续(如一条法规被切断) */
    private int chunkOverlap = 80;

    /** 检索召回切片数 */
    private int topK = 5;

    /** 相似度阈值,低于此值判定知识库无相关内容 */
    private double similarityThreshold = 0.65;

    /** 多轮对话带入 LLM 的最大历史消息数(10 条 = 最近 5 轮问答),超出的更早消息截断 */
    private int maxHistoryMessages = 10;

    /** 检索结果缓存开关:相同问题第二次起跳过 Embedding 调用,直接命中 Redis */
    private boolean cacheEnabled = true;

    /** 命中结果缓存 TTL(秒),默认 30 分钟 */
    private long cacheTtlSeconds = 1800;

    /** 空结果缓存 TTL(秒):短 TTL 缓存"无命中",防止恶意用不存在的问题刷 Embedding 接口(缓存穿透) */
    private long emptyCacheTtlSeconds = 300;

    /** 问答接口限流:单个 IP 在窗口内最多请求次数 */
    private int rateLimitMax = 20;

    /** 限流窗口(秒) */
    private long rateLimitWindowSeconds = 60;

    /** 与 LLM/Embedding 接口建立 TCP 连接的超时(秒) */
    private long connectTimeoutSeconds = 5;

    /** 非流式调用读取响应超时(秒):长答案生成较慢,给到 60s */
    private long readTimeoutSeconds = 60;

    /** 流式回答"两个 token 之间"的最大空闲间隔(秒),超过判定生成卡死并降级 */
    private long streamIdleTimeoutSeconds = 30;
}
