package com.example.rag.llm;

import com.example.rag.config.LlmProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 按配置装配所有模型客户端。当前只负责「找到默认模型」，
 * M4 阶段在这里加入路由策略与失败降级。
 */
@Slf4j
@Component
public class LlmClientRegistry {

    private final Map<String, LlmClient> clients = new LinkedHashMap<>();
    private final String defaultProvider;

    public LlmClientRegistry(LlmProperties properties, ObjectMapper objectMapper) {
        this.defaultProvider = properties.getDefaultProvider();
        properties.getProviders().forEach((name, config) -> {
            if (!config.isEnabled()) {
                log.info("模型提供方 {} 已禁用，跳过注册", name);
                return;
            }
            clients.put(name, new OpenAiCompatibleLlmClient(name, config, objectMapper));
            log.info("已注册模型提供方：{} -> {} ({})", name, config.getBaseUrl(), config.getChatModel());
        });
        log.info("模型提供方共 {} 个，默认使用：{}", clients.size(), defaultProvider);
    }

    public Optional<LlmClient> find(String name) {
        return Optional.ofNullable(clients.get(name));
    }

    public LlmClient defaultClient() {
        LlmClient client = clients.get(defaultProvider);
        if (client == null) {
            throw new IllegalStateException("默认模型提供方未注册：" + defaultProvider);
        }
        return client;
    }

    public Map<String, LlmClient> all() {
        return Map.copyOf(clients);
    }
}
