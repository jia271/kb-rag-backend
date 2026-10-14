package com.example.rag.retrieval;

/**
 * 一条检索结果。keywordRank / vectorRank 记录它在各路召回里的名次，
 * 保留这两个字段是为了让"为什么这条排在前面"可解释——面试时经常被问到这个。
 */
public record SearchHit(
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

    public SearchHit withVectorRank(Integer rank) {
        return new SearchHit(chunkId, documentId, fileName, orderIndex, pageNumber, heading, content,
                keywordRank, rank, score);
    }

    public SearchHit withScore(double newScore) {
        return new SearchHit(chunkId, documentId, fileName, orderIndex, pageNumber, heading, content,
                keywordRank, vectorRank, newScore);
    }
}
