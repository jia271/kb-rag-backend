package com.example.rag.document.api.dto;

import java.util.List;

public record ChunkPageResponse(
        long total,
        int page,
        int size,
        List<ChunkResponse> items
) {
}
