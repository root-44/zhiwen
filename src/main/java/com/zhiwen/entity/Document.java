package com.zhiwen.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档元数据实体
 * 状态机:PARSING(解析中) → CHUNKING(切片中) → READY(就绪,可被检索) / FAILED(失败)
 */
@Data
@TableName("kb_document")
public class Document {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 展示标题,默认取文件名去后缀 */
    private String title;

    /** 原始文件名 */
    private String fileName;

    /** pdf / docx / doc / md / txt */
    private String fileType;

    /** 文件字节数 */
    private Long fileSize;

    /** 解析出的总字符数 */
    private Integer charCount;

    /** 切片总数 */
    private Integer chunkCount;

    /** PARSING / CHUNKING / READY / FAILED */
    private String status;

    /** 失败原因(status=FAILED 时有值) */
    private String errorMsg;

    /** 逻辑删除:0 正常,1 已删 */
    @TableLogic
    private Integer deleted;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
