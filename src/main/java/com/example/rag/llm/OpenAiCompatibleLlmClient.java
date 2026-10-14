package com.example.rag.llm;

import com.example.rag.config.LlmProperties;
import com.example.rag.llm.dto.ChatCompletionResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelOption;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.util.List;
import java.util.Map;

/**
 * 基于 OpenAI 兼容协议的模型客户端。不依赖任何厂商 SDK，
 * 换个 base-url 就能接另一家（这也是后面做多模型路由的基础）。
 */
@Slf4j
public class OpenAiCompatibleLlmClient implements LlmClient {

    private static final int CONNECT_TIMEOUT_MILLIS = 10_000;
    private static final int MAX_IN_MEMORY_SIZE = 8 * 1024 * 1024;
    private static final String STREAM_DONE = "[DONE]";

    private final String providerName;
    private final LlmProperties.Provider provider;
    private final ObjectMapper objectMapper;
    private final WebClient webClient;

    public OpenAiCompatibleLlmClient(String providerName, LlmProperties.Provider provider, ObjectMapper objectMapper) {
        this.providerName = providerName;
        this.provider = provider;
        this.objectMapper = objectMapper;

        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECT_TIMEOUT_MILLIS)
                .responseTimeout(provider.getTimeout());

        this.webClient = WebClient.builder()
                .baseUrl(stripTrailingSlash(provider.getBaseUrl()))
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + provider.getApiKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(MAX_IN_MEMORY_SIZE))
                .build();
    }

    @Override
    public String providerName() {
        return providerName;
    }

    @Override
    public boolean available() {
        return provider.getApiKey() != null && !provider.getApiKey().isBlank();
    }

    @Override
    public Mono<ChatResult> chat(List<ChatMessage> messages) {
        long startNanos = System.nanoTime();
        return webClient.post()
                .uri("/chat/completions")
                .bodyValue(buildRequestBody(messages, false))
                .retrieve()
                .bodyToMono(ChatCompletionResponse.class)
                .map(response -> new ChatResult(
                        providerName,
                        response.model() == null ? provider.getChatModel() : response.model(),
                        response.firstContent(),
                        promptTokens(response),
                        completionTokens(response),
                        (System.nanoTime() - startNanos) / 1_000_000));
    }

    /**
     * 流式对话。OpenAI 兼容协议的响应用 SSE 承载，每个 data 段是一个增量片段，
     * 最后以 data: [DONE] 结束。
     */
    @Override
    public Flux<String> streamChat(List<ChatMessage> messages) {
        return webClient.post()
                .uri("/chat/completions")
                .bodyValue(buildRequestBody(messages, true))
                .retrieve()
                .bodyToFlux(String.class)
                .takeUntil(STREAM_DONE::equals)
                .filter(payload -> !STREAM_DONE.equals(payload))
                .concatMap(this::contentOf)
                .filter(text -> !text.isEmpty());
    }

    private Mono<String> contentOf(String payload) {
        try {
            JsonNode delta = objectMapper.readTree(payload)
                    .path("choices").path(0).path("delta").path("content");
            return delta.isMissingNode() || delta.isNull() ? Mono.empty() : Mono.just(delta.asText());
        } catch (JsonProcessingException error) {
            log.warn("无法解析流式响应片段，已跳过：{}", payload);
            return Mono.empty();
        }
    }

    private Map<String, Object> buildRequestBody(List<ChatMessage> messages, boolean stream) {
        List<Map<String, String>> wireMessages = messages.stream()
                .map(message -> Map.of("role", message.role(), "content", message.content()))
                .toList();
        return Map.of(
                "model", provider.getChatModel(),
                "messages", wireMessages,
                "stream", stream);
    }

    private int promptTokens(ChatCompletionResponse response) {
        return response.usage() == null || response.usage().promptTokens() == null
                ? 0 : response.usage().promptTokens();
    }

    private int completionTokens(ChatCompletionResponse response) {
        return response.usage() == null || response.usage().completionTokens() == null
                ? 0 : response.usage().completionTokens();
    }

    private static String stripTrailingSlash(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("模型提供方 base-url 未配置");
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
