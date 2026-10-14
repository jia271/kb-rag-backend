package com.example.rag.retrieval;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reciprocal Rank Fusion：把多路召回结果合成一个排序。
 *
 * <p>为什么用名次而不是分数：BM25 的分数和向量相似度的量纲完全不同，
 * 直接加权求和要先做归一化，而归一化又要靠调参。RRF 只用"排第几"，
 * 天然免疫量纲问题，实现简单且效果稳定，是混合检索里最常用的融合方式。
 */
public final class RrfFusion {

    private RrfFusion() {
    }

    public static <T> Map<T, Double> fuse(int rrfK, List<List<T>> rankedLists) {
        Map<T, Double> scores = new LinkedHashMap<>();
        int k = Math.max(1, rrfK);
        for (List<T> ranked : rankedLists) {
            for (int index = 0; index < ranked.size(); index++) {
                // 名次从 1 开始，所以是 index + 1
                scores.merge(ranked.get(index), 1.0 / (k + index + 1), Double::sum);
            }
        }
        return scores;
    }
}
