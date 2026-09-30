package com.zhiwen.common.ratelimit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口限流标记:按客户端 IP 做固定窗口计数,阈值/窗口在 application.yml 统一配置。
 * 可标注在 Controller 方法或类上(方法优先)。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {
}
