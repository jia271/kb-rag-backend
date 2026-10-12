package com.example.rag.llm;

/**
 * 一次对话调用的结果，带上 token 数与耗时——M4 的计量和监控都依赖这两个字段。
 */
public record ChatResult(
        String provider,
        String model,
        String content,
        int promptTokens,
        int completionTokens,
        long latencyMillis
) {
    public int totalTokens() {
        return promptTokens + completionTokens;
    }
}
