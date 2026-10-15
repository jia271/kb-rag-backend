package com.example.rag.chat;

import com.example.rag.config.ChatProperties;
import com.example.rag.llm.ChatMessage;
import com.example.rag.llm.LlmCallContext;
import com.example.rag.llm.LlmGateway;
import com.example.rag.llm.StreamChunk;
import com.example.rag.retrieval.HybridSearchService;
import com.example.rag.retrieval.SearchHit;
import com.example.rag.retrieval.SearchMode;
import com.example.rag.support.BlockingCalls;
import com.example.rag.support.Errors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 问答编排：检索 → 组装提示词 → 生成答案 → 标注引用。
 *
 * <p>检索是阻塞的（Lucene + JPA），生成是流式的，所以整条链路用
 * {@link BlockingCalls} 把阻塞部分挪到弹性线程池，避免堵住 WebFlux 的事件循环。
 */
@Slf4j
@Service
public class ChatService {

    /** 检索不到任何依据时的固定回答：不调用模型，既省钱也避免编造 */
    public static final String NO_CONTEXT_ANSWER =
            "知识库中没有找到与该问题相关的内容。请先上传相关文档，或者换一种说法再问。";

    private final HybridSearchService searchService;
    private final LlmGateway llmGateway;
    private final RagPromptBuilder promptBuilder;
    private final RelevanceGate relevanceGate;
    private final ConversationStore conversations;
    private final ChatProperties properties;

    public ChatService(HybridSearchService searchService,
                       LlmGateway llmGateway,
                       RagPromptBuilder promptBuilder,
                       RelevanceGate relevanceGate,
                       ConversationStore conversations,
                       ChatProperties properties) {
        this.searchService = searchService;
        this.llmGateway = llmGateway;
        this.promptBuilder = promptBuilder;
        this.relevanceGate = relevanceGate;
        this.conversations = conversations;
        this.properties = properties;
    }

    public Flux<ChatStreamEvent> stream(String question, String sessionId, Integer topK, String mode) {
        String session = resolveSessionId(sessionId);
        long start = System.currentTimeMillis();

        return BlockingCalls.call(() -> prepare(question, session, topK, mode))
                .flatMapMany(prepared -> streamAnswer(prepared, question, session, start))
                .onErrorResume(error -> {
                    log.warn("问答失败：{}", Errors.rootMessage(error));
                    return Flux.just(new ChatStreamEvent.Error(Errors.rootMessage(error)));
                });
    }

    public Mono<AskResult> ask(String question, String sessionId, Integer topK, String mode) {
        String session = resolveSessionId(sessionId);
        long start = System.currentTimeMillis();

        return BlockingCalls.call(() -> prepare(question, session, topK, mode))
                .flatMap(prepared -> {
                    if (prepared.hits().isEmpty()) {
                        conversations.append(session, question, NO_CONTEXT_ANSWER);
                        return Mono.just(new AskResult(session, NO_CONTEXT_ANSWER, prepared.citations(),
                                System.currentTimeMillis() - start, 0, 0));
                    }
                    return llmGateway.chat(prepared.messages(), LlmCallContext.of(session, "chat"))
                            .map(result -> {
                                conversations.append(session, question, result.content());
                                return new AskResult(session, result.content(), prepared.citations(),
                                        System.currentTimeMillis() - start,
                                        result.promptTokens(), result.completionTokens());
                            });
                });
    }

    private Flux<ChatStreamEvent> streamAnswer(Prepared prepared, String question, String session, long start) {
        Flux<ChatStreamEvent> head = Flux.<ChatStreamEvent>just(new ChatStreamEvent.Citations(prepared.citations()));

        if (prepared.hits().isEmpty()) {
            conversations.append(session, question, NO_CONTEXT_ANSWER);
            return head.concatWith(Flux.<ChatStreamEvent>just(new ChatStreamEvent.Completed(
                    NO_CONTEXT_ANSWER, 0, System.currentTimeMillis() - start, session, 0, 0)));
        }

        StringBuilder answer = new StringBuilder();
        long[] firstTokenMillis = {0};
        AtomicReference<int[]> usage = new AtomicReference<>(new int[]{0, 0});

        Flux<ChatStreamEvent> body = llmGateway
                .stream(prepared.messages(), LlmCallContext.of(session, "stream"))
                .doOnNext(chunk -> {
                    if (chunk.hasText() && firstTokenMillis[0] == 0) {
                        // 首字延迟：从收到提问到吐出第一个字的时间，是流式体验最关键的指标
                        firstTokenMillis[0] = System.currentTimeMillis() - start;
                    }
                    if (chunk.hasText()) {
                        answer.append(chunk.delta());
                    }
                    if (chunk.hasUsage()) {
                        usage.set(new int[]{
                                chunk.promptTokens() == null ? 0 : chunk.promptTokens(),
                                chunk.completionTokens() == null ? 0 : chunk.completionTokens()});
                    }
                })
                .filter(StreamChunk::hasText)
                .map(chunk -> (ChatStreamEvent) new ChatStreamEvent.Delta(chunk.delta()))
                .concatWith(Mono.<ChatStreamEvent>fromCallable(() -> {
                    String text = answer.toString();
                    conversations.append(session, question, text);
                    int[] tokens = usage.get();
                    return new ChatStreamEvent.Completed(
                            text, firstTokenMillis[0], System.currentTimeMillis() - start, session,
                            tokens[0], tokens[1]);
                }));

        return head.concatWith(body);
    }

    private Prepared prepare(String question, String sessionId, Integer topK, String mode) throws Exception {
        int limit = topK == null || topK <= 0 ? properties.getTopK() : topK;
        SearchMode searchMode = SearchMode.from(mode);
        List<SearchHit> hits = relevanceGate.filter(searchService.search(question, limit, searchMode));
        // 这一行跑在弹性线程池里，追踪号会由 BlockingCalls 还原到 MDC，
        // 所以日志前缀里能看出它属于哪一次请求
        log.info("检索完成：命中 {} 条片段（模式 {}）", hits.size(), searchMode.name().toLowerCase());
        List<ChatMessage> messages = promptBuilder.build(question, hits, conversations.history(sessionId));
        return new Prepared(hits, messages, Citation.from(hits));
    }

    private String resolveSessionId(String sessionId) {
        return sessionId == null || sessionId.isBlank()
                ? UUID.randomUUID().toString()
                : sessionId.trim();
    }

    private record Prepared(List<SearchHit> hits, List<ChatMessage> messages, List<Citation> citations) {
    }
}
