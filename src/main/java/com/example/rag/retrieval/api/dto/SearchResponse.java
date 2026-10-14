package com.example.rag.retrieval.api.dto;

import java.util.List;

public record SearchResponse(
        String query,
        String mode,
        String embeddingProvider,
        int embeddingDimensions,
        long tookMillis,
        int indexedChunks,
        List<SearchHitResponse> hits
) {
}
