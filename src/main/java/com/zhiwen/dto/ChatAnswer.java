package com.zhiwen.dto;

import java.util.List;

/**
 * 问答结果(非流式)
 *
 * @param answer       LLM 生成的回答
 * @param sources      引用的切片列表(前端渲染"参考来源",可点开看原文/页码)
 * @param fromKnowledge 是否命中知识库;false 表示知识库无相关内容(走的降级话术,未调用 LLM)
 */
public record ChatAnswer(
        String answer,
        List<SearchHit> sources,
        boolean fromKnowledge
) {
}
