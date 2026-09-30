package com.zhiwen.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ChatClient 配置
 *
 * <p>ChatClient 是 Spring AI 1.0 编排 LLM 调用的统一入口(类似 RestTemplate 之于 HTTP),
 * 底层 ChatModel 由 starter 根据 application.yml 自动装配为百炼 qwen-plus。
 */
@Configuration
public class ChatClientConfig {

    @Bean
    public ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.create(chatModel);
    }
}
