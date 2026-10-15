package com.example.rag.chat.api.dto;

import com.example.rag.chat.AskResult;
import com.example.rag.chat.Citation;

import java.util.List;

public record AskResponse(
        String sessionId,
        String answer,
        List<Citation> citations,
        long tookMillis,
        int promptTokens,
        int completionTokens
) {

    public static AskResponse from(AskResult result) {
        return new AskResponse(result.sessionId(), result.answer(), result.citations(), result.tookMillis(),
                result.promptTokens(), result.completionTokens());
    }
}
