package com.example.rag.retrieval.api.dto;

public record ReindexResponse(
        int documents,
        int chunks,
        long tookMillis,
        String index
) {
}
