package com.example.rag.chat;

import java.util.List;

public record AskResult(
        String sessionId,
        String answer,
        List<Citation> citations,
        long tookMillis,
        int promptTokens,
        int completionTokens
) {
}
