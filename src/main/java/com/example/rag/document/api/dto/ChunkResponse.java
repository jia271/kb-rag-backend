package com.example.rag.document.api.dto;

import com.example.rag.document.domain.DocumentChunk;

public record ChunkResponse(
        int orderIndex,
        int charCount,
        Integer pageNumber,
        String heading,
        String content
) {

    public static ChunkResponse from(DocumentChunk chunk) {
        return new ChunkResponse(
                chunk.getOrderIndex(),
                chunk.getCharCount(),
                chunk.getPageNumber(),
                chunk.getHeading(),
                chunk.getContent());
    }
}
