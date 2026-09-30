package com.zhiwen.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档切片实体:切片文本存这张表,向量本体存 vector_store(PgVector)
 * vector_id 关联向量表主键,实现"回答 → 切片 → 原文"的引用溯源链路
 */
@Data
@TableName("kb_chunk")
public class DocumentChunk {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属文档 id */
    private Long documentId;

    /** 切片序号,从 0 开始 */
    private Integer seq;

    /** 切片文本 */
    private String content;

    /** 字符数 */
    private Integer charCount;

    /** PDF 页码(非 PDF 文档为 null) */
    private Integer pageNo;

    /** PENDING / DONE / FAILED */
    private String embeddingStatus;

    /** vector_store 表主键 */
    private String vectorId;

    private LocalDateTime createdAt;
}
