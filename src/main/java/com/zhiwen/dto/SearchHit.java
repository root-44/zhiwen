package com.zhiwen.dto;

/**
 * 单条检索命中结果
 *
 * @param chunkId    切片 id(回表取原文)
 * @param documentId 文档 id
 * @param title      文档标题
 * @param content    切片文本(喂给 LLM 的上下文)
 * @param seq        切片序号
 * @param pageNo     PDF 页码(引用溯源用,非 PDF 为 null)
 * @param score      相似度分数(余弦,越接近 1 越相关;模型未回填时为 null)
 */
public record SearchHit(
        Long chunkId,
        Long documentId,
        String title,
        String content,
        Integer seq,
        Integer pageNo,
        Double score
) {
}
