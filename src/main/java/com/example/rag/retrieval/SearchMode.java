package com.example.rag.retrieval;

public enum SearchMode {

    /** 只看关键词（BM25），擅长精确匹配专有名词、错误码 */
    KEYWORD,

    /** 只看语义向量，擅长换一种说法的提问 */
    VECTOR,

    /** 两路召回后融合排序，通常最稳 */
    HYBRID;

    public static SearchMode from(String value) {
        if (value == null || value.isBlank()) {
            return HYBRID;
        }
        return switch (value.trim().toLowerCase()) {
            case "keyword", "bm25" -> KEYWORD;
            case "vector", "knn", "semantic" -> VECTOR;
            default -> HYBRID;
        };
    }
}
