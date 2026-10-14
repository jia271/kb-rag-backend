package com.example.rag.embedding;

import com.example.rag.config.EmbeddingProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 装配所有向量化客户端。配置的提供方不可用时（例如没填密钥）
 * 自动降级到本地兜底实现，保证链路不会因为缺密钥直接瘫掉。
 */
@Slf4j
@Component
public class EmbeddingRegistry {

    public static final String LOCAL_PROVIDER = "local";

    private final Map<String, EmbeddingClient> clients = new LinkedHashMap<>();
    private final EmbeddingClient fallback;
    private final EmbeddingClient defaultClient;

    public EmbeddingRegistry(EmbeddingProperties properties) {
        EmbeddingProperties.Provider localConfig = properties.getProviders().get(LOCAL_PROVIDER);
        int localDimensions = localConfig == null ? 512 : localConfig.getDimensions();
        this.fallback = new LocalHashingEmbeddingClient(LOCAL_PROVIDER, localDimensions);
        this.clients.put(LOCAL_PROVIDER, fallback);

        properties.getProviders().forEach((name, config) -> {
            if (LOCAL_PROVIDER.equals(name) || !config.isEnabled()) {
                return;
            }
            EmbeddingClient client = new OpenAiCompatibleEmbeddingClient(name, config);
            this.clients.put(name, client);
            log.info("已注册向量化提供方：{} -> {} ({}, {} 维)",
                    name, config.getBaseUrl(), config.getModel(), config.getDimensions());
        });

        EmbeddingClient configured = clients.get(properties.getDefaultProvider());
        if (configured != null && configured.available()) {
            this.defaultClient = configured;
            log.info("向量化使用：{}（{} 维）", configured.providerName(), configured.dimensions());
        } else {
            this.defaultClient = fallback;
            log.warn("配置的向量化提供方 {} 不可用（可能没填密钥），已降级为本地兜底实现："
                            + "链路能跑通，但语义检索效果会明显下降",
                    properties.getDefaultProvider());
        }
    }

    public EmbeddingClient defaultClient() {
        return defaultClient;
    }

    public Optional<EmbeddingClient> find(String name) {
        return Optional.ofNullable(clients.get(name));
    }

    public Map<String, EmbeddingClient> all() {
        return Map.copyOf(clients);
    }
}
