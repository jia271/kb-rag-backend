package com.example.rag.retrieval.api.dto;

import com.example.rag.retrieval.SearchHit;

public record SearchHitResponse(
        long chunkId,
        long documentId,
        String fileName,
        int orderIndex,
        Integer pageNumber,
        String heading,
        String content,
        Integer keywordRank,
        Integer vectorRank,
        double score
) {

    public static SearchHitResponse from(SearchHit hit) {
        return new SearchHitResponse(
                hit.chunkId(),
                hit.documentId(),
                hit.fileName(),
                hit.orderIndex(),
                hit.pageNumber(),
                hit.heading(),
                hit.content(),
                hit.keywordRank(),
                hit.vectorRank(),
                hit.score());
    }
}
