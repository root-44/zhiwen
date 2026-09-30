package com.zhiwen.controller;

import com.zhiwen.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康检查接口:D1 骨架验收用,确认 Web / PostgreSQL / Redis 三条链路通
 * 前端启动后先调这个接口,避免页面白屏时不知道是哪一层挂了
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/health")
@RequiredArgsConstructor
public class HealthController {

    private final DataSource dataSource;
    private final StringRedisTemplate stringRedisTemplate;

    @GetMapping
    public ApiResponse<Map<String, Object>> health() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("service", "zhiwen-backend");
        status.put("version", "1.0.0");
        status.put("uptimeMs", ManagementFactoryHolder.runtimeMs());
        status.put("postgres", checkPostgres());
        status.put("redis", checkRedis());
        return ApiResponse.ok(status);
    }

    private String checkPostgres() {
        try (Connection conn = dataSource.getConnection()) {
            return conn.isValid(3) ? "UP" : "DOWN";
        } catch (Exception e) {
            log.warn("PostgreSQL 健康检查失败: {}", e.getMessage());
            return "DOWN: " + e.getMessage();
        }
    }

    private String checkRedis() {
        try {
            return "PONG".equals(stringRedisTemplate.getConnectionFactory()
                    .getConnection().ping()) ? "UP" : "DOWN";
        } catch (Exception e) {
            log.warn("Redis 健康检查失败: {}", e.getMessage());
            return "DOWN: " + e.getMessage();
        }
    }

    /** 小工具:返回 JVM 已运行毫秒数,单独放静态内部类避免 import 污染 */
    private static final class ManagementFactoryHolder {
        static long runtimeMs() {
            return java.lang.management.ManagementFactory.getRuntimeMXBean().getUptime();
        }
    }
}
