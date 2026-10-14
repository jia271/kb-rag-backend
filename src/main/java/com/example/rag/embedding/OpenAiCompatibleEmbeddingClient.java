package com.example.rag.embedding;

import com.example.rag.config.EmbeddingProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * OpenAI 兼容协议的 /embeddings 实现。硅基流动、通义、智谱、OpenAI 都能接。
 */
@Slf4j
public class OpenAiCompatibleEmbeddingClient implements EmbeddingClient {

    private final String providerName;
    private final EmbeddingProperties.Provider provider;
    private final RestClient restClient;

    public OpenAiCompatibleEmbeddingClient(String providerName, EmbeddingProperties.Provider provider) {
        this.providerName = providerName;
        this.provider = provider;
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory();
        requestFactory.setReadTimeout(provider.getTimeout());
        this.restClient = RestClient.builder()
                .baseUrl(stripTrailingSlash(provider.getBaseUrl()))
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + provider.getApiKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public String providerName() {
        return providerName;
    }

    @Override
    public boolean available() {
        return provider.getApiKey() != null && !provider.getApiKey().isBlank()
                && provider.getModel() != null && !provider.getModel().isBlank();
    }

    @Override
    public int dimensions() {
        return provider.getDimensions();
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        if (texts.isEmpty()) {
            return List.of();
        }
        EmbeddingResponse response = restClient.post()
                .uri("/embeddings")
                .body(new EmbeddingRequest(provider.getModel(), texts))
                .retrieve()
                .body(EmbeddingResponse.class);

        if (response == null || response.data() == null || response.data().size() != texts.size()) {
            throw new IllegalStateException(providerName + " 返回的向量数量与请求不一致");
        }

        List<EmbeddingItem> items = new ArrayList<>(response.data());
        items.sort(Comparator.comparingInt(EmbeddingItem::index));

        List<float[]> vectors = new ArrayList<>(items.size());
        for (EmbeddingItem item : items) {
            List<Float> values = item.embedding();
            float[] vector = new float[values.size()];
            for (int i = 0; i < values.size(); i++) {
                vector[i] = values.get(i);
            }
            if (vector.length != dimensions()) {
                throw new IllegalStateException("模型返回 " + vector.length + " 维，但配置写的是 "
                        + dimensions() + " 维，请修正 app.embedding.providers." + providerName + ".dimensions");
            }
            vectors.add(vector);
        }
        return vectors;
    }

    private static String stripTrailingSlash(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("向量化提供方的 base-url 未配置");
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private record EmbeddingRequest(String model, List<String> input) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record EmbeddingResponse(String model, List<EmbeddingItem> data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record EmbeddingItem(int index, List<Float> embedding) {
    }
}
