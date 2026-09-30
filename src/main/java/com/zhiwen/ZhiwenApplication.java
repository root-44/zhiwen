package com.zhiwen;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 智问 - 领域知识库问答系统启动类
 *
 * <p>技术亮点(简历/面试):
 * <ul>
 *   <li>Spring AI 1.0 GA:ChatClient + EmbeddingModel + PgVector VectorStore</li>
 *   <li>RAG 全链路:文档切片 → 向量化 → 语义检索 → Prompt 拼装 → SSE 流式回答</li>
 * </ul>
 */
@SpringBootApplication
@MapperScan("com.zhiwen.mapper")
@ConfigurationPropertiesScan("com.zhiwen.config")
public class ZhiwenApplication {

    public static void main(String[] args) {
        SpringApplication.run(ZhiwenApplication.class, args);
    }
}
