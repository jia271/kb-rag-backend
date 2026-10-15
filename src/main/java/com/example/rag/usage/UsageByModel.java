package com.example.rag.usage;

/**
 * 按提供方与模型聚合的用量。字段用包装类型，因为 JPQL 的 sum/count 返回的是 Long/Double。
 */
public record UsageByModel(
        String provider,
        String model,
        Long promptTokens,
        Long completionTokens,
        Long calls,
        Double cost,
        Long latencyMillis
) {
}
