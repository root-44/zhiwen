package com.zhiwen.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiwen.common.ApiResponse;
import com.zhiwen.common.ratelimit.RateLimit;
import com.zhiwen.dto.ChatAnswer;
import com.zhiwen.dto.ChatQuestion;
import com.zhiwen.dto.SearchHit;
import com.zhiwen.service.chat.RagChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 问答接口
 * - POST /api/v1/chat          非流式,一次返回完整答案 + 引用来源(适合调试/无 EventSource 环境)
 * - GET  /api/v1/chat/stream   SSE 流式,打字机效果(前端 EventSource 消费)
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatController {

    private final RagChatService ragChatService;
    private final ObjectMapper objectMapper;

    /** 非流式问答 */
    @RateLimit
    @PostMapping
    public ApiResponse<ChatAnswer> chat(@Valid @RequestBody ChatQuestion req) {
        List<SearchHit> sources = ragChatService.retrieve(req.getQuestion().trim());
        boolean fromKnowledge = !sources.isEmpty();
        String answer = ragChatService.answer(req.getQuestion().trim(), sources);
        return ApiResponse.ok(new ChatAnswer(answer, sources, fromKnowledge));
    }

    /**
     * SSE 流式问答
     * 事件协议:
     *   event: sources  data: 引用切片 JSON 数组(首帧,前端先渲染"参考来源")
     *   event: token    data: 增量文本(JSON 字符串)
     *   event: done     data: {"fromKnowledge":true}
     *   event: error    data: 错误信息(JSON 字符串)
     */
    @RateLimit
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> stream(@RequestParam("q") String question) {
        String q = question == null ? "" : question.trim();
        if (q.isEmpty()) {
            return Flux.just(event("error", "问题不能为空"));
        }

        // 检索在建立 SSE 前同步完成:命中结果作为首帧推给前端
        List<SearchHit> sources;
        try {
            sources = ragChatService.retrieve(q);
        } catch (Exception e) {
            log.error("检索失败 q='{}'", q, e);
            return Flux.just(event("error", "检索失败: " + e.getMessage()));
        }

        boolean fromKnowledge = !sources.isEmpty();
        ServerSentEvent<String> sourcesFrame = ServerSentEvent.<String>builder()
                .event("sources")
                .data(toJson(sources))
                .build();

        ServerSentEvent<String> doneFrame = ServerSentEvent.<String>builder()
                .event("done")
                .data("{\"fromKnowledge\":" + fromKnowledge + "}")
                .build();

        Flux<ServerSentEvent<String>> tokens = ragChatService.streamAnswer(q, sources)
                .map(text -> ServerSentEvent.<String>builder()
                        .event("token")
                        .data(toJson(text))
                        .build())
                .onErrorResume(e -> {
                    log.error("LLM 流式生成失败 q='{}'", q, e);
                    return Flux.just(event("error", "生成失败: " + e.getMessage()));
                });

        // sources → token* → done
        return Flux.concat(Flux.just(sourcesFrame), tokens, Flux.just(doneFrame));
    }

    private ServerSentEvent<String> event(String name, String data) {
        return ServerSentEvent.<String>builder().event(name).data(toJson(data)).build();
    }

    /** SSE data 帧统一用 JSON 编码,避免中文/换行破坏 SSE 协议 */
    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "\"序列化失败\"";
        }
    }
}
