package com.zhiwen.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话内消息
 * 注意:类名用 ChatMessage,避免与 Spring AI 的 org.springframework.ai.chat.messages.Message 冲突
 */
@Data
@TableName("kb_message")
public class ChatMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long conversationId;

    /** user / assistant */
    private String role;

    private String content;

    /** assistant 消息的引用来源 JSON(SearchHit 列表序列化),user 消息为 null */
    private String sources;

    /** assistant 消息是否命中知识库:1 是,0 否 */
    private Integer fromKnowledge;

    /** 会话内序号,从 0 开始 */
    private Integer seq;

    private LocalDateTime createdAt;
}
