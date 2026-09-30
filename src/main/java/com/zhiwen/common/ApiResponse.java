package com.zhiwen.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.UUID;

/**
 * 统一响应结构:与智耕云枢后端保持一致,前端适配层可直接复用
 *
 * <pre>
 * { "success": true, "data": {...}, "message": "操作成功", "error": null, "traceId": "xxx" }
 * </pre>
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private boolean success;
    private T data;
    private String message;
    private Object error;
    private String traceId;

    private ApiResponse(boolean success, T data, String message, Object error) {
        this.success = success;
        this.data = data;
        this.message = message;
        this.error = error;
        this.traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, "操作成功", null);
    }

    public static <T> ApiResponse<T> ok(T data, String message) {
        return new ApiResponse<>(true, data, message, null);
    }

    public static <T> ApiResponse<T> fail(ErrorCode errorCode) {
        return new ApiResponse<>(false, null, errorCode.getMessage(),
                java.util.Map.of("code", errorCode.name(), "message", errorCode.getMessage()));
    }

    public static <T> ApiResponse<T> fail(ErrorCode errorCode, String detail) {
        return new ApiResponse<>(false, null, detail,
                java.util.Map.of("code", errorCode.name(), "message", detail));
    }
}
