package com.example.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 模型接入配置。用 Map 保存多个提供方，是为后面的「多模型路由 + 降级」留的口子。
 */
@Data
@ConfigurationProperties(prefix = "app.llm")
public class LlmProperties {

    /** 默认使用的模型提供方 */
    private String defaultProvider = "deepseek";

    /**
     * 降级顺序。排在前面的先试，失败后依次尝试后面的。
     * 留空则只使用默认提供方。
     */
    private List<String> fallbackOrder = new ArrayList<>();

    /** 提供方名称 -> 连接配置 */
    private Map<String, Provider> providers = new LinkedHashMap<>();

    @Data
    public static class Provider {

        /** 是否启用，方便按需开关某个供应商 */
        private boolean enabled = true;

        /** OpenAI 兼容协议的 base url，例如 https://api.deepseek.com */
        private String baseUrl;

        /** API Key，通过环境变量注入，不写进代码和仓库 */
        private String apiKey;

        /** 对话模型名 */
        private String chatModel;

        /** 单次请求超时 */
        private Duration timeout = Duration.ofSeconds(60);

        /**
         * 计费单价，单位：元 / 千 token。
         * 这里的默认值只是占位，请按你自己账户的实际计费填写。
         */
        private double pricePer1kPrompt = 0.0;

        private double pricePer1kCompletion = 0.0;
    }
}
