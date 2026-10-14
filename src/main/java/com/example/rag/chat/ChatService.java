package com.example.rag.chat;

import com.example.rag.config.ChatProperties;
import com.example.rag.llm.ChatMessage;
import com.example.rag.llm.LlmClientRegistry;
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
    private final LlmClientRegistry llmRegistry;
    private final RagPromptBuilder promptBuilder;
    private final ConversationStore conversations;
    private final ChatProperties properties;

    public ChatService(HybridSearchService searchService,
                       LlmClientRegistry llmRegistry,
                       RagPromptBuilder promptBuilder,
                       ConversationStore conversations,
                       ChatProperties properties) {
        this.searchService = searchService;
        this.llmRegistry = llmRegistry;
        this.promptBuilder = promptBuilder;
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
                                System.currentTimeMillis() - start));
                    }
                    return llmRegistry.defaultClient().chat(prepared.messages())
                            .map(result -> {
                                conversations.append(session, question, result.content());
                                return new AskResult(session, result.content(), prepared.citations(),
                                        System.currentTimeMillis() - start);
                            });
                });
    }

    private Flux<ChatStreamEvent> streamAnswer(Prepared prepared, String question, String session, long start) {
        Flux<ChatStreamEvent> head = Flux.<ChatStreamEvent>just(new ChatStreamEvent.Citations(prepared.citations()));

        if (prepared.hits().isEmpty()) {
            conversations.append(session, question, NO_CONTEXT_ANSWER);
            return head.concatWith(Flux.<ChatStreamEvent>just(new ChatStreamEvent.Completed(
                    NO_CONTEXT_ANSWER, 0, System.currentTimeMillis() - start, session)));
        }

        StringBuilder answer = new StringBuilder();
        long[] firstTokenMillis = {0};

        Flux<ChatStreamEvent> body = llmRegistry.defaultClient()
                .streamChat(prepared.messages())
                .doOnNext(delta -> {
                    if (firstTokenMillis[0] == 0) {
                        // 首字延迟：从收到提问到吐出第一个字的时间，是流式体验最关键的指标
                        firstTokenMillis[0] = System.currentTimeMillis() - start;
                    }
                    answer.append(delta);
                })
                .map(delta -> (ChatStreamEvent) new ChatStreamEvent.Delta(delta))
                .concatWith(Mono.<ChatStreamEvent>fromCallable(() -> {
                    String text = answer.toString();
                    conversations.append(session, question, text);
                    return new ChatStreamEvent.Completed(
                            text, firstTokenMillis[0], System.currentTimeMillis() - start, session);
                }));

        return head.concatWith(body);
    }

    private Prepared prepare(String question, String sessionId, Integer topK, String mode) throws Exception {
        int limit = topK == null || topK <= 0 ? properties.getTopK() : topK;
        List<SearchHit> hits = searchService.search(question, limit, SearchMode.from(mode));
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
