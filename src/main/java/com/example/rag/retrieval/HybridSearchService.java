package com.example.rag.retrieval;

import com.example.rag.config.SearchProperties;
import com.example.rag.embedding.EmbeddingRegistry;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 检索编排：单路召回直接透传，混合模式做两路召回 + RRF 融合。
 */
@Service
public class HybridSearchService {

    private final ChunkIndexService index;
    private final EmbeddingRegistry embeddingRegistry;
    private final SearchProperties searchProperties;

    public HybridSearchService(ChunkIndexService index,
                               EmbeddingRegistry embeddingRegistry,
                               SearchProperties searchProperties) {
        this.index = index;
        this.embeddingRegistry = embeddingRegistry;
        this.searchProperties = searchProperties;
    }

    public List<SearchHit> search(String query, int topK, SearchMode mode) throws IOException {
        int limit = topK <= 0 ? searchProperties.getTopK() : topK;
        return switch (mode) {
            case KEYWORD -> index.searchKeyword(query, limit);
            case VECTOR -> index.searchVector(embeddingRegistry.defaultClient().embedOne(query), limit);
            case HYBRID -> hybrid(query, limit);
        };
    }

    private List<SearchHit> hybrid(String query, int topK) throws IOException {
        // 每路多取一些，给融合留出空间，最后再截断到 topK
        int perList = Math.max(topK * 2, topK);
        List<SearchHit> keywordHits = index.searchKeyword(query, perList);
        List<SearchHit> vectorHits =
                index.searchVector(embeddingRegistry.defaultClient().embedOne(query), perList);

        Map<Long, SearchHit> merged = new LinkedHashMap<>();
        for (SearchHit hit : keywordHits) {
            merged.put(hit.chunkId(), hit);
        }
        for (SearchHit hit : vectorHits) {
            merged.merge(hit.chunkId(), hit, (existing, incoming) -> existing.withVectorRank(incoming.vectorRank()));
        }

        Map<Long, Double> fused = RrfFusion.fuse(
                searchProperties.getRrfK(),
                List.of(ids(keywordHits), ids(vectorHits)));

        List<SearchHit> results = new ArrayList<>(merged.size());
        for (SearchHit hit : merged.values()) {
            results.add(hit.withScore(fused.getOrDefault(hit.chunkId(), 0.0)));
        }
        results.sort(Comparator.comparingDouble(SearchHit::score).reversed());
        return results.size() > topK ? new ArrayList<>(results.subList(0, topK)) : results;
    }

    private List<Long> ids(List<SearchHit> hits) {
        return hits.stream().map(SearchHit::chunkId).toList();
    }
}
