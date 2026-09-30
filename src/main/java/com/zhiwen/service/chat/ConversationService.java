package com.zhiwen.service.chat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiwen.common.BusinessException;
import com.zhiwen.common.ErrorCode;
import com.zhiwen.config.RagProperties;
import com.zhiwen.dto.SearchHit;
import com.zhiwen.entity.ChatMessage;
import com.zhiwen.entity.Conversation;
import com.zhiwen.mapper.ChatMessageMapper;
import com.zhiwen.mapper.ConversationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

/**
 * 多轮对话编排:会话管理 + 上下文窗口 + 检索词改写 + 历史/回答落库
 *
 * <p>上下文管理策略(面试点):
 * <ul>
 *   <li>窗口截断:只把最近 maxHistoryMessages 条消息喂给 LLM,控制 token 成本和延迟,
 *       更早的对话丢弃 —— 滑动窗口策略</li>
 *   <li>检索词改写:追问常含代词("它用什么药"),纯当前句向量检索会跑偏;
 *       把最近一条用户问题拼到当前问题前一起向量化,消解指代,无需额外一次 LLM 调用</li>
 *   <li>资料只在当前轮注入:历史轮的参考资料不重复携带,避免无关旧资料稀释相关性</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationMapper conversationMapper;
    private final ChatMessageMapper messageMapper;
    private final RagChatService ragChatService;
    private final RagProperties props;
    private final ObjectMapper objectMapper;

    /** 流式问答的中间结果:sources 首帧用,tokens 给 Controller 包 SSE */
    public record StreamChatResult(List<SearchHit> sources, boolean fromKnowledge, Flux<String> tokens) {
    }

    public Conversation create() {
        Conversation conv = new Conversation();
        conv.setTitle("新会话");
        conv.setMessageCount(0);
        conversationMapper.insert(conv);
        return conv;
    }

    public List<Conversation> list() {
        return conversationMapper.selectList(
                new LambdaQueryWrapper<Conversation>().orderByDesc(Conversation::getUpdatedAt));
    }

    public List<ChatMessage> messages(Long conversationId) {
        getOrThrow(conversationId);
        return messageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getConversationId, conversationId)
                .orderByAsc(ChatMessage::getSeq));
    }

    public void delete(Long conversationId) {
        getOrThrow(conversationId);
        conversationMapper.deleteById(conversationId);
        messageMapper.delete(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getConversationId, conversationId));
    }

    /** 非流式多轮问答 */
    public ChatMessage ask(Long conversationId, String question) {
        Conversation conv = getOrThrow(conversationId);
        String q = question.trim();

        // 1. 取上下文历史 + 改写检索词
        List<ChatMessage> historyMsgs = recentMessages(conversationId);
        List<Message> llmHistory = toLlmMessages(historyMsgs);
        String searchQuery = rewriteQuery(historyMsgs, q);

        // 2. 落库 user 消息
        int nextSeq = messageCount(conversationId);
        saveMessage(conversationId, "user", q, null, null, nextSeq);

        // 3. 检索 + 生成
        List<SearchHit> sources = ragChatService.retrieve(searchQuery);
        boolean fromKnowledge = !sources.isEmpty();
        String answer = ragChatService.answerInContext(llmHistory, q, sources);

        // 4. 落库 assistant 消息
        ChatMessage reply = saveMessage(conversationId, "assistant", answer,
                fromKnowledge ? toJson(sources) : null, fromKnowledge ? 1 : 0, nextSeq + 1);

        touchConversation(conv, q);
        return reply;
    }

    /**
     * 流式多轮问答
     * 注意:user 消息在建立流之前落库;assistant 消息在 token 流结束(doOnComplete)时聚合落库
     */
    public StreamChatResult askStream(Long conversationId, String question) {
        Conversation conv = getOrThrow(conversationId);
        String q = question.trim();

        List<ChatMessage> historyMsgs = recentMessages(conversationId);
        List<Message> llmHistory = toLlmMessages(historyMsgs);
        String searchQuery = rewriteQuery(historyMsgs, q);

        int nextSeq = messageCount(conversationId);
        saveMessage(conversationId, "user", q, null, null, nextSeq);

        List<SearchHit> sources = ragChatService.retrieve(searchQuery);
        boolean fromKnowledge = !sources.isEmpty();
        String sourcesJson = fromKnowledge ? toJson(sources) : null;

        StringBuilder fullAnswer = new StringBuilder();
        Flux<String> tokens = ragChatService.streamInContext(llmHistory, q, sources)
                .doOnNext(fullAnswer::append)
                .doOnComplete(() -> {
                    saveMessage(conversationId, "assistant", fullAnswer.toString(),
                            sourcesJson, fromKnowledge ? 1 : 0, nextSeq + 1);
                    touchConversation(conv, q);
                })
                .doOnError(e -> log.error("流式问答落库前失败 conv={}", conversationId, e));

        return new StreamChatResult(sources, fromKnowledge, tokens);
    }

    /** 取最近 N 条历史消息,按时间正序返回(LLM 要求时序从旧到新) */
    private List<ChatMessage> recentMessages(Long conversationId) {
        List<ChatMessage> desc = messageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getConversationId, conversationId)
                .orderByDesc(ChatMessage::getSeq)
                .last("LIMIT " + props.getMaxHistoryMessages()));
        java.util.Collections.reverse(desc);
        return desc;
    }

    /** 业务消息 → Spring AI 消息;历史回答只带文本,不带旧参考资料 */
    private List<Message> toLlmMessages(List<ChatMessage> msgs) {
        List<Message> result = new ArrayList<>();
        for (ChatMessage m : msgs) {
            if ("user".equals(m.getRole())) {
                result.add(new UserMessage(m.getContent()));
            } else {
                result.add(new AssistantMessage(m.getContent()));
            }
        }
        return result;
    }

    /**
     * 检索词改写:存在历史时,把最近一条用户问题拼接到当前问题前。
     * 例:历史问"穗颈瘟怎么防治?",当前问"它用什么药?" → 检索词含两个句子,
     * "它"的语义被锚定到穗颈瘟,向量召回才不会跑偏
     */
    private String rewriteQuery(List<ChatMessage> history, String current) {
        for (int i = history.size() - 1; i >= 0; i--) {
            if ("user".equals(history.get(i).getRole())) {
                return history.get(i).getContent() + " " + current;
            }
        }
        return current;
    }

    private ChatMessage saveMessage(Long convId, String role, String content,
                                    String sourcesJson, Integer fromKnowledge, int seq) {
        ChatMessage m = new ChatMessage();
        m.setConversationId(convId);
        m.setRole(role);
        m.setContent(content);
        m.setSources(sourcesJson);
        m.setFromKnowledge(fromKnowledge);
        m.setSeq(seq);
        messageMapper.insert(m);
        return m;
    }

    /** 首轮(会话还没标题)用问题前 20 字当标题,并刷新消息计数/时间 */
    private void touchConversation(Conversation conv, String firstQuestion) {
        if ("新会话".equals(conv.getTitle())) {
            conv.setTitle(firstQuestion.length() > 20 ? firstQuestion.substring(0, 20) : firstQuestion);
        }
        conv.setMessageCount(messageCount(conv.getId()));
        conversationMapper.updateById(conv);
    }

    private int messageCount(Long conversationId) {
        return Math.toIntExact(messageMapper.selectCount(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getConversationId, conversationId)));
    }

    private Conversation getOrThrow(Long id) {
        Conversation conv = conversationMapper.selectById(id);
        if (conv == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "会话不存在: " + id);
        }
        return conv;
    }

    private String toJson(List<SearchHit> sources) {
        try {
            return objectMapper.writeValueAsString(sources);
        } catch (JsonProcessingException e) {
            log.warn("引用来源序列化失败", e);
            return null;
        }
    }
}
