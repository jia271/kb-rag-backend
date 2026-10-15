package com.example.rag.llm;

import com.example.rag.config.LlmProperties;
import com.example.rag.support.Errors;
import com.example.rag.usage.UsageRecorder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 模型调用的统一入口：按顺序尝试多个提供方，失败自动切换，并记录用量。
 *
 * <p>降级有两个必须分清的边界：
 * <ul>
 *   <li>非流式调用失败：随便切，用户还没看到任何内容。</li>
 *   <li>流式调用：只有在**还没吐出任何字**的时候才能切。已经开始输出再换模型，
 *       用户会看到前后两段风格不一致的拼接内容，比直接报错更糟。</li>
 * </ul>
 */
@Slf4j
@Component
public class LlmGateway {

    private final List<LlmClient> candidates;
    private final UsageRecorder usageRecorder;

    @Autowired
    public LlmGateway(LlmClientRegistry registry, LlmProperties properties, UsageRecorder usageRecorder) {
        this(orderCandidates(registry, properties), usageRecorder);
    }

    /** 直接给定候选列表，便于测试降级逻辑 */
    LlmGateway(List<LlmClient> candidates, UsageRecorder usageRecorder) {
        this.usageRecorder = usageRecorder;
        this.candidates = List.copyOf(candidates);
        if (candidates.isEmpty()) {
            log.warn("没有任何可用的模型提供方，问答功能不可用");
        } else {
            log.info("模型路由顺序：{}", providerOrder());
        }
    }

    public List<String> providerOrder() {
        return candidates.stream().map(LlmClient::providerName).toList();
    }

    public Mono<ChatResult> chat(List<ChatMessage> messages, LlmCallContext context) {
        return attemptChat(0, messages, context);
    }

    public Flux<StreamChunk> stream(List<ChatMessage> messages, LlmCallContext context) {
        return Flux.defer(() -> attemptStream(0, messages, context));
    }

    private Mono<ChatResult> attemptChat(int index, List<ChatMessage> messages, LlmCallContext context) {
        if (index >= candidates.size()) {
            return Mono.error(new IllegalStateException("所有模型提供方都调用失败：" + providerOrder()));
        }
        LlmClient client = candidates.get(index);
        long start = System.currentTimeMillis();

        return client.chat(messages)
                .doOnNext(result -> usageRecorder.record(context, result.provider(), result.model(),
                        result.promptTokens(), result.completionTokens(),
                        System.currentTimeMillis() - start, true, null))
                .onErrorResume(error -> {
                    String reason = Errors.rootMessage(error);
                    log.warn("[{}] 提供方 {} 调用失败：{}", context.traceId(), client.providerName(), reason);
                    usageRecorder.record(context, client.providerName(), client.modelName(), 0, 0,
                            System.currentTimeMillis() - start, false, reason);
                    return attemptChat(index + 1, messages, context);
                });
    }

    private Flux<StreamChunk> attemptStream(int index, List<ChatMessage> messages, LlmCallContext context) {
        if (index >= candidates.size()) {
            return Flux.error(new IllegalStateException("所有模型提供方都调用失败：" + providerOrder()));
        }
        LlmClient client = candidates.get(index);
        long start = System.currentTimeMillis();
        AtomicBoolean emitted = new AtomicBoolean(false);
        AtomicReference<int[]> usage = new AtomicReference<>(new int[]{0, 0});

        return client.streamChat(messages)
                .doOnNext(chunk -> {
                    if (chunk.hasText()) {
                        emitted.set(true);
                    }
                    if (chunk.hasUsage()) {
                        usage.set(new int[]{
                                chunk.promptTokens() == null ? 0 : chunk.promptTokens(),
                                chunk.completionTokens() == null ? 0 : chunk.completionTokens()});
                    }
                })
                .doOnComplete(() -> {
                    int[] tokens = usage.get();
                    if (tokens[0] == 0 && tokens[1] == 0) {
                        log.debug("提供方 {} 没有回传用量，本次按 0 计", client.providerName());
                    }
                    usageRecorder.record(context, client.providerName(), client.modelName(),
                            tokens[0], tokens[1], System.currentTimeMillis() - start, true, null);
                })
                .onErrorResume(error -> {
                    String reason = Errors.rootMessage(error);
                    int[] tokens = usage.get();
                    usageRecorder.record(context, client.providerName(), client.modelName(),
                            tokens[0], tokens[1], System.currentTimeMillis() - start, false, reason);
                    if (emitted.get()) {
                        log.warn("[{}] 提供方 {} 输出中途失败，不能再降级：{}",
                                context.traceId(), client.providerName(), reason);
                        return Flux.error(error);
                    }
                    log.warn("[{}] 提供方 {} 未产出内容即失败，降级到下一个：{}",
                            context.traceId(), client.providerName(), reason);
                    return attemptStream(index + 1, messages, context);
                });
    }

    /** 默认提供方排最前，然后按配置的降级顺序，最后补上没写进顺序里的 */
    private static List<LlmClient> orderCandidates(LlmClientRegistry registry, LlmProperties properties) {
        Map<String, LlmClient> registered = registry.all();
        LinkedHashSet<String> names = new LinkedHashSet<>();
        names.add(properties.getDefaultProvider());
        names.addAll(properties.getFallbackOrder());
        names.addAll(registered.keySet());

        List<LlmClient> ordered = new ArrayList<>();
        for (String name : names) {
            LlmClient client = registered.get(name);
            if (client == null) {
                continue;
            }
            if (!client.available()) {
                log.warn("模型提供方 {} 不可用（可能没配密钥），已跳过", name);
                continue;
            }
            ordered.add(client);
        }
        return List.copyOf(ordered);
    }
}
