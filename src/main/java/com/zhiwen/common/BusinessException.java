package com.zhiwen.common;

import lombok.Getter;

/**
 * 业务异常:Service 层主动抛出,由 GlobalExceptionHandler 统一转成 ApiResponse
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String detail) {
        super(detail);
        this.errorCode = errorCode;
    }
}
