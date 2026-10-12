package com.example.rag.llm;

import com.example.rag.config.LlmProperties;
import com.example.rag.llm.dto.ChatCompletionResponse;
import io.netty.channel.ChannelOption;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.util.List;
import java.util.Map;

/**
 * 基于 OpenAI 兼容协议的模型客户端。不依赖任何厂商 SDK，
 * 换个 base-url 就能接另一家（这也是后面做多模型路由的基础）。
 */
public class OpenAiCompatibleLlmClient implements LlmClient {

    private static final int CONNECT_TIMEOUT_MILLIS = 10_000;
    private static final int MAX_IN_MEMORY_SIZE = 8 * 1024 * 1024;

    private final String providerName;
    private final LlmProperties.Provider provider;
    private final WebClient webClient;

    public OpenAiCompatibleLlmClient(String providerName, LlmProperties.Provider provider) {
        this.providerName = providerName;
        this.provider = provider;

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
    public Mono<ChatResult> chat(String prompt) {
        long startNanos = System.nanoTime();
        return webClient.post()
                .uri("/chat/completions")
                .bodyValue(buildRequestBody(prompt))
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

    private Map<String, Object> buildRequestBody(String prompt) {
        return Map.of(
                "model", provider.getChatModel(),
                "messages", List.of(Map.of("role", "user", "content", prompt)),
                "stream", false);
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
