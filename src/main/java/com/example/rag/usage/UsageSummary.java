package com.example.rag.usage;

import java.util.List;

public record UsageSummary(
        long calls,
        long successCalls,
        long promptTokens,
        long completionTokens,
        double cost,
        double averageLatencyMillis,
        List<UsageByModel> byModel
) {
}
