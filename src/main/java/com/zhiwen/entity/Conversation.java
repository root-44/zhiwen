package com.zhiwen.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 多轮对话会话 */
@Data
@TableName("kb_conversation")
public class Conversation {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 会话标题,默认首轮问题前 20 字 */
    private String title;

    private Integer messageCount;

    @TableLogic
    private Integer deleted;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
