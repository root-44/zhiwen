package com.zhiwen.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiwen.common.ApiResponse;
import com.zhiwen.common.ratelimit.RateLimit;
import com.zhiwen.dto.ChatQuestion;
import com.zhiwen.entity.ChatMessage;
import com.zhiwen.entity.Conversation;
import com.zhiwen.service.chat.ConversationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 多轮对话接口
 * - POST   /api/v1/conversations               创建会话
 * - GET    /api/v1/conversations               会话列表
 * - GET    /api/v1/conversations/{id}/messages 历史消息
 * - DELETE /api/v1/conversations/{id}          删除会话
 * - POST   /api/v1/conversations/{id}/chat     非流式多轮问答
 * - GET    /api/v1/conversations/{id}/chat/stream  SSE 流式多轮问答
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;
    private final ObjectMapper objectMapper;

    @PostMapping
    public ApiResponse<Conversation> create() {
        return ApiResponse.ok(conversationService.create(), "会话已创建");
    }

    @GetMapping
    public ApiResponse<List<Conversation>> list() {
        return ApiResponse.ok(conversationService.list());
    }

    @GetMapping("/{id}/messages")
    public ApiResponse<List<ChatMessage>> messages(@PathVariable Long id) {
        return ApiResponse.ok(conversationService.messages(id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        conversationService.delete(id);
        return ApiResponse.ok(null, "会话已删除");
    }

    /** 非流式多轮问答 */
    @RateLimit
    @PostMapping("/{id}/chat")
    public ApiResponse<ChatMessage> chat(@PathVariable Long id, @Valid @RequestBody ChatQuestion req) {
        return ApiResponse.ok(conversationService.ask(id, req.getQuestion()));
    }

    /**
     * SSE 流式多轮问答,事件协议与单轮一致:sources / token / done / error
     */
    @RateLimit
    @GetMapping(value = "/{id}/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> stream(@PathVariable Long id,
                                                @RequestParam("q") String question) {
        String q = question == null ? "" : question.trim();
        if (q.isEmpty()) {
            return Flux.just(event("error", "问题不能为空"));
        }

        final ConversationService.StreamChatResult result;
        try {
            result = conversationService.askStream(id, q);
        } catch (Exception e) {
            log.error("流式问答启动失败 conv={}", id, e);
            return Flux.just(event("error", e.getMessage()));
        }

        ServerSentEvent<String> sourcesFrame = ServerSentEvent.<String>builder()
                .event("sources")
                .data(toJson(result.sources()))
                .build();
        ServerSentEvent<String> doneFrame = ServerSentEvent.<String>builder()
                .event("done")
                .data("{\"fromKnowledge\":" + result.fromKnowledge() + "}")
                .build();

        Flux<ServerSentEvent<String>> tokens = result.tokens()
                .map(text -> ServerSentEvent.<String>builder().event("token").data(toJson(text)).build())
                .onErrorResume(e -> {
                    log.error("流式生成失败 conv={}", id, e);
                    return Flux.just(event("error", "生成失败: " + e.getMessage()));
                });

        return Flux.concat(Flux.just(sourcesFrame), tokens, Flux.just(doneFrame));
    }

    private ServerSentEvent<String> event(String name, String data) {
        return ServerSentEvent.<String>builder().event(name).data(toJson(data)).build();
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "\"序列化失败\"";
        }
    }
}
