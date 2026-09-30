package com.zhiwen.common.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiwen.common.ApiResponse;
import com.zhiwen.common.ErrorCode;
import com.zhiwen.config.RagProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;

/**
 * 基于 Redis 固定窗口的 IP 限流拦截器
 *
 * <p>算法:key = rag:rl:{ip}:{窗口起点秒},窗口内 INCR 计数,首次计数时 EXPIRE 设窗口过期。
 * 计数超过阈值直接返回 429,请求不进入 Controller —— 把 LLM 这种昂贵调用挡在最前面。
 *
 * <p>为什么用 Redis 而不是单机 ConcurrentHashMap:
 * 多实例部署时本地计数各算各的,限不住;Redis 集中计数,全局限额一致。
 *
 * <p>固定窗口的已知边界:窗口切换瞬间可能通过 2 倍请求(上一窗口尾部 + 下一窗口头部各一批)。
 * 本项目问答接口成本可控,该边界可接受;若要严格平滑可换滑动窗口 Lua/ZSET。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final String KEY_PREFIX = "rag:rl:";

    private final StringRedisTemplate redis;
    private final RagProperties props;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        // 只在初始请求分派时限流:SSE 接口返回 Flux 会触发 ASYNC 再分派,
        // 异步分派也会重跑拦截器,若不排除会导致一次流式请求被计数两次
        if (request.getDispatcherType() != jakarta.servlet.DispatcherType.REQUEST) {
            return true;
        }
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        // 方法上的注解优先,其次类上
        RateLimit rateLimit = handlerMethod.getMethodAnnotation(RateLimit.class);
        if (rateLimit == null) {
            rateLimit = handlerMethod.getBeanType().getAnnotation(RateLimit.class);
        }
        if (rateLimit == null) {
            return true;
        }

        String ip = clientIp(request);
        long window = props.getRateLimitWindowSeconds();
        long windowStart = System.currentTimeMillis() / 1000 / window * window;
        String key = KEY_PREFIX + ip + ":" + windowStart;

        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redis.expire(key, java.time.Duration.ofSeconds(window));
        }

        if (count != null && count > props.getRateLimitMax()) {
            log.warn("限流触发 ip={} count={}/{}", ip, count, props.getRateLimitMax());
            write429(response);
            return false;
        }
        return true;
    }

    /** 取真实客户端 IP:优先反向代理注入的 X-Forwarded-For(取第一个,即最初客户端) */
    private String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank() && !"unknown".equalsIgnoreCase(xff)) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    private void write429(HttpServletResponse response) throws Exception {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Retry-After", String.valueOf(props.getRateLimitWindowSeconds()));
        ApiResponse<Void> body = ApiResponse.fail(ErrorCode.RATE_LIMITED);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
