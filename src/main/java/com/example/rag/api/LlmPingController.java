package com.example.rag.api;

import com.example.rag.llm.LlmClient;
import com.example.rag.llm.LlmClientRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * M0 阶段的自检接口：验证密钥、网络与模型调用是否打通。
 * 等真正的问答接口上线后，这个接口会被移除或降级为内部诊断用。
 */
@Slf4j
@RestController
@RequestMapping("/api/llm")
public class LlmPingController {

    private final LlmClientRegistry registry;

    public LlmPingController(LlmClientRegistry registry) {
        this.registry = registry;
    }

    @GetMapping(value = "/ping", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Map<String, Object>> ping(
            @RequestParam(defaultValue = "用一句话解释什么是检索增强生成（RAG）") String q,
            @RequestParam(required = false) String provider) {

        LlmClient client = (provider == null || provider.isBlank())
                ? registry.defaultClient()
                : registry.find(provider)
                        .orElseThrow(() -> new IllegalArgumentException("未注册的模型提供方：" + provider));

        if (!client.available()) {
            return Mono.just(failure(client.providerName(),
                    "未配置 API Key，请设置环境变量 DEEPSEEK_API_KEY 后重启服务"));
        }

        return client.chat(q)
                .map(result -> {
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("ok", true);
                    body.put("provider", result.provider());
                    body.put("model", result.model());
                    body.put("question", q);
                    body.put("answer", result.content());
                    body.put("promptTokens", result.promptTokens());
                    body.put("completionTokens", result.completionTokens());
                    body.put("latencyMillis", result.latencyMillis());
                    return body;
                })
                .onErrorResume(error -> {
                    log.warn("调用模型失败：{}", error.toString());
                    return Mono.just(failure(client.providerName(), rootMessage(error)));
                });
    }

    private Map<String, Object> failure(String provider, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", false);
        body.put("provider", provider);
        body.put("error", message);
        return body;
    }

    private String rootMessage(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause.getClass().getSimpleName() + ": " + cause.getMessage();
    }
}
