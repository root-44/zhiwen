package com.zhiwen.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Spring AI 非流式调用(Chat / Embedding)的 HTTP 超时定制
 *
 * <p>Spring AI 1.0 默认使用 JDK 原生 HttpClient(项目未引入 reactor-netty/Apache HttpClient5)。
 * Spring AI 自动配置消费容器中的 RestClient.Builder,因此用 Spring Boot 的
 * {@link RestClientCustomizer} 即可统一给所有非流式模型调用装上超时:
 * <ul>
 *   <li>connectTimeout:建连超时,网络不通/防火墙丢包时快速失败,而不是长时间挂起</li>
 *   <li>readTimeout:读取响应超时(每次 HTTP read),长答案给到 60s</li>
 * </ul>
 *
 * <p>流式(SSE)调用走 WebClient,是长连接,不能用总读取超时;其"空闲超时"在
 * RagChatService 用 Reactor timeout() 单独控制。
 */
@Configuration
@RequiredArgsConstructor
public class HttpClientTimeoutConfig {

    private final RagProperties props;

    @Bean
    public RestClientCustomizer modelApiTimeoutCustomizer() {
        HttpClient jdkClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(props.getConnectTimeoutSeconds()))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(jdkClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(props.getReadTimeoutSeconds()));
        return builder -> builder.requestFactory(requestFactory);
    }
}
