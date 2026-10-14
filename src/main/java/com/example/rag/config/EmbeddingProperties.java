package com.example.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 向量化配置。结构与模型配置一致，同样支持多家切换。
 */
@Data
@ConfigurationProperties(prefix = "app.embedding")
public class EmbeddingProperties {

    /** 默认使用的向量化提供方 */
    private String defaultProvider = "local";

    /** 一次请求最多送几条文本，避免单次请求过大 */
    private int batchSize = 16;

    private Map<String, Provider> providers = new LinkedHashMap<>();

    @Data
    public static class Provider {

        private boolean enabled = true;

        private String baseUrl;

        private String apiKey;

        /** 向量模型名 */
        private String model;

        /**
         * 向量维度。必须和模型真实输出一致，否则索引会报错。
         * 本地兜底实现由这个值决定生成多少维。
         */
        private int dimensions = 512;

        private Duration timeout = Duration.ofSeconds(60);
    }
}
