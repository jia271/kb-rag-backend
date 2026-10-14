package com.example.rag.document.api.dto;

import com.example.rag.document.domain.Document;

import java.time.Instant;

public record DocumentResponse(
        Long id,
        String fileName,
        String contentType,
        long sizeBytes,
        String status,
        int chunkCount,
        String failureReason,
        Instant createdAt,
        Instant parsedAt
) {

    public static DocumentResponse from(Document document) {
        return new DocumentResponse(
                document.getId(),
                document.getFileName(),
                document.getContentType(),
                document.getSizeBytes(),
                document.getStatus().name(),
                document.getChunkCount(),
                document.getFailureReason(),
                document.getCreatedAt(),
                document.getParsedAt());
    }
}
