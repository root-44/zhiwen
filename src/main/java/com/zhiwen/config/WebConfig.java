package com.zhiwen.config;

import com.zhiwen.common.ratelimit.RateLimitInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 配置:开发期允许前端 Vite(5173)跨域访问;注册限流拦截器
 * 生产环境跨域应由网关/Nginx 统一处理
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final RateLimitInterceptor rateLimitInterceptor;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(
                        "http://localhost:5173", "http://127.0.0.1:5173",  // 智耕云枢前端
                        "http://localhost:5174", "http://127.0.0.1:5174"   // 智问前端
                )
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 拦截所有 /api,是否限流由方法/类上的 @RateLimit 注解决定
        registry.addInterceptor(rateLimitInterceptor).addPathPatterns("/api/**");
    }
}
