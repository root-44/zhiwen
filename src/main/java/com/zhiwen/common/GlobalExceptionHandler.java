package com.zhiwen.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 全局异常处理:Controller 抛出的异常在这里统一转成 ApiResponse,避免堆栈泄露给前端
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 外部依赖(LLM/向量库)不可用类错误,语义上是 503 而非 400 */
    private static final Set<ErrorCode> DEPENDENCY_UNAVAILABLE = EnumSet.of(
            ErrorCode.RAG_LLM_TIMEOUT, ErrorCode.RAG_LLM_FAIL, ErrorCode.RAG_SEARCH_FAIL);

    /** 业务异常:参数/资源类 400,外部依赖不可用类 503 */
    @ExceptionHandler(BusinessException.class)
    public org.springframework.http.ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
        log.warn("业务异常: {} - {}", ex.getErrorCode(), ex.getMessage());
        HttpStatus status = DEPENDENCY_UNAVAILABLE.contains(ex.getErrorCode())
                ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.BAD_REQUEST;
        return org.springframework.http.ResponseEntity.status(status)
                .body(ApiResponse.fail(ex.getErrorCode(), ex.getMessage()));
    }

    /** @Valid 参数校验异常:拼接所有字段错误信息 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return ApiResponse.fail(ErrorCode.PARAM_INVALID, detail);
    }

    /** 兜底:未预期异常返回 500,日志里打完整堆栈 */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleUnexpected(Exception ex) {
        log.error("系统异常", ex);
        return ApiResponse.fail(ErrorCode.INTERNAL_ERROR);
    }
}
