package com.zhiwen.common;

import lombok.Getter;

/**
 * 业务错误码枚举
 * 命名规范:域_错误,如 DOC_PARSE_FAIL / RAG_LLM_TIMEOUT
 */
@Getter
public enum ErrorCode {

    // 通用 4xx
    PARAM_INVALID("参数校验失败"),
    NOT_FOUND("资源不存在"),
    UNAUTHORIZED("未登录或登录已过期"),
    RATE_LIMITED("请求过于频繁,请稍后再试"),

    // 文档域
    DOC_EMPTY("上传文件为空"),
    DOC_FORMAT_UNSUPPORTED("不支持的文档格式"),
    DOC_PARSE_FAIL("文档解析失败"),

    // RAG 域
    RAG_NO_KNOWLEDGE("知识库为空,请先导入文档"),
    RAG_LLM_TIMEOUT("AI 模型响应超时"),
    RAG_LLM_FAIL("AI 生成服务暂时不可用"),
    RAG_SEARCH_FAIL("知识检索服务暂时不可用"),
    RAG_EMBEDDING_FAIL("文本向量化失败"),

    // 系统
    INTERNAL_ERROR("系统内部错误");

    private final String message;

    ErrorCode(String message) {
        this.message = message;
    }
}
