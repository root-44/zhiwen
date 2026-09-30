package com.zhiwen.service.chat;

import com.zhiwen.common.BusinessException;
import com.zhiwen.common.ErrorCode;
import com.zhiwen.config.RagProperties;
import com.zhiwen.dto.SearchHit;
import com.zhiwen.service.search.RagSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;

/**
 * RAG 问答编排:检索 → 拼 Prompt → LLM 生成
 *
 * <p>防幻觉设计(面试核心):
 * <ul>
 *   <li>System Prompt 强约束:只能依据参考资料回答,资料不足必须承认不知道</li>
 *   <li>检索 0 命中直接返回降级话术,不调用 LLM —— 既防"无依据乱编",又省 token 和延迟</li>
 *   <li>参考资料带 [n] 编号,要求模型引用标注,回答可溯源到具体切片/页码</li>
 * </ul>
 *
 * <p>多轮对话:history 只放最近 N 条问答(由 ConversationService 完成窗口截断),
 * 当前轮的参考资料始终通过最新 user 消息注入,防止旧资料污染。
 *
 * <p>容错与降级(D7):
 * <ul>
 *   <li>检索故障(Embedding/向量库挂):抛 RAG_SEARCH_FAIL,由全局处理器返回 503 ——
 *       不能把"检索异常"误判成"知识库无答案",否则会对用户撒谎说没有资料</li>
 *   <li>LLM 生成故障但检索成功:不报错,降级为"已检索到 N 条资料请先查看",保住检索成果</li>
 *   <li>流式空闲超时:两个 token 间隔超过阈值判定生成卡死,推一条降级提示后正常收尾</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagChatService {

    private final RagSearchService ragSearchService;
    private final ChatClient chatClient;
    private final RagProperties props;

    /** 知识库无答案时的固定话术 */
    public static final String NO_KNOWLEDGE_REPLY =
            "根据现有知识库资料,暂时无法回答该问题。你可以换个说法,或向知识库中导入相关文档后再试。";

    /** LLM 生成失败但已检索到资料时的降级话术模板,%d 为资料条数 */
    public static final String LLM_FALLBACK_TEMPLATE =
            "【AI 生成服务暂时不可用,以下是为你检索到的 %d 条参考资料,请先查看下方来源,稍后再试生成回答】";

    public static final String SYSTEM_PROMPT = """
            你是「智问」农业领域知识库助手。请严格遵守以下规则:
            1. 只能依据用户提供的【参考资料】回答,不得使用资料之外的知识,严禁编造数据(尤其是药剂名、剂量、浓度、比例)。
            2. 如果参考资料不足以回答问题,直接回复"根据现有知识库资料,暂时无法回答该问题。",不要强行作答。
            3. 在引用资料内容的句末用 [n] 标注来源编号(n 与参考资料编号一致)。
            4. 用简体中文回答,条理清晰;涉及防治措施时优先分点列出。
            5. 支持结合对话历史理解用户的指代(如"它""这个病"),但回答依据仍只能来自当前轮的参考资料。
            """;

    /**
     * 语义检索。检索链路本身故障(Embedding 服务/向量库不可用)时抛 503 业务异常,
     * 与"检索成功但无命中"(返回空列表)严格区分。
     */
    public List<SearchHit> retrieve(String query) {
        try {
            return ragSearchService.search(query);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            // 根因(超时/连接拒绝/上游错误码)只进日志,不把上游报文、模型名等内部细节透传给前端
            log.error("知识检索失败 q='{}',根因: {}", query, rootMessage(e), e);
            throw new BusinessException(ErrorCode.RAG_SEARCH_FAIL);
        }
    }

    /** 单轮非流式问答 */
    public String answer(String question, List<SearchHit> sources) {
        return answerInContext(List.of(), question, sources);
    }

    /** 单轮流式问答 */
    public Flux<String> streamAnswer(String question, List<SearchHit> sources) {
        return streamInContext(List.of(), question, sources);
    }

    /** 多轮非流式问答:LLM 调用失败时降级,不把异常抛给用户 */
    public String answerInContext(List<Message> history, String question, List<SearchHit> sources) {
        if (sources == null || sources.isEmpty()) {
            return NO_KNOWLEDGE_REPLY;
        }
        try {
            return chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .messages(history)
                    .user(buildUserPrompt(question, sources))
                    .call()
                    .content();
        } catch (Exception e) {
            log.error("LLM 非流式生成失败,降级到参考资料 q='{}'", question, e);
            return String.format(LLM_FALLBACK_TEMPLATE, sources.size());
        }
    }

    /** 多轮流式问答:空闲超时 / 生成异常都降级为一条提示,保证前端正常收到 done */
    public Flux<String> streamInContext(List<Message> history, String question, List<SearchHit> sources) {
        if (sources == null || sources.isEmpty()) {
            return Flux.just(NO_KNOWLEDGE_REPLY);
        }
        return chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .messages(history)
                .user(buildUserPrompt(question, sources))
                .stream()
                .content()
                // 只在"长时间没有新 token"时触发,不限制回答总时长(长答案可以慢慢出)
                .timeout(Duration.ofSeconds(props.getStreamIdleTimeoutSeconds()))
                .onErrorResume(e -> {
                    log.error("LLM 流式生成失败/超时,降级 q='{}'", question, e);
                    return Flux.just(String.format(LLM_FALLBACK_TEMPLATE, sources.size()));
                });
    }

    /**
     * 拼装 User Prompt:编号参考资料 + 来源标注(标题/页码)+ 问题
     * 页码信息对农业手册类 PDF 很关键,方便用户翻到原书核对
     */
    public String buildUserPrompt(String question, List<SearchHit> sources) {
        StringBuilder sb = new StringBuilder("【参考资料】\n");
        for (int i = 0; i < sources.size(); i++) {
            SearchHit hit = sources.get(i);
            sb.append("[").append(i + 1).append("] 来源:《").append(hit.title()).append("》");
            if (hit.pageNo() != null) {
                sb.append("(第").append(hit.pageNo()).append("页)");
            }
            sb.append("\n").append(hit.content().trim()).append("\n\n");
        }
        sb.append("【用户问题】\n").append(question);
        return sb.toString();
    }

    /** 取异常链最底层消息,便于日志定位(超时/连接拒绝等) */
    private String rootMessage(Throwable e) {
        Throwable cur = e;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        String msg = cur.getMessage();
        return msg == null ? cur.getClass().getSimpleName() : msg;
    }
}
