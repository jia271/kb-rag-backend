package com.example.rag.retrieval.api;

import com.example.rag.config.SearchProperties;
import com.example.rag.embedding.EmbeddingRegistry;
import com.example.rag.retrieval.ChunkIndexService;
import com.example.rag.retrieval.HybridSearchService;
import com.example.rag.retrieval.IndexMaintenanceService;
import com.example.rag.retrieval.SearchHit;
import com.example.rag.retrieval.SearchMode;
import com.example.rag.retrieval.api.dto.ReindexResponse;
import com.example.rag.retrieval.api.dto.SearchHitResponse;
import com.example.rag.retrieval.api.dto.SearchResponse;
import com.example.rag.support.BlockingCalls;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 检索接口。M2 阶段的成果都在这里：可以分别或同时使用关键词与向量召回。
 */
@RestController
@RequestMapping("/api/search")
public class SearchController {

    private static final int MAX_TOP_K = 50;

    private final HybridSearchService searchService;
    private final IndexMaintenanceService maintenanceService;
    private final ChunkIndexService index;
    private final EmbeddingRegistry embeddingRegistry;
    private final SearchProperties searchProperties;

    public SearchController(HybridSearchService searchService,
                            IndexMaintenanceService maintenanceService,
                            ChunkIndexService index,
                            EmbeddingRegistry embeddingRegistry,
                            SearchProperties searchProperties) {
        this.searchService = searchService;
        this.maintenanceService = maintenanceService;
        this.index = index;
        this.embeddingRegistry = embeddingRegistry;
        this.searchProperties = searchProperties;
    }

    @GetMapping
    public Mono<SearchResponse> search(@RequestParam("q") String query,
                                       @RequestParam(required = false) Integer topK,
                                       @RequestParam(defaultValue = "hybrid") String mode) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("检索词不能为空");
        }
        String trimmed = query.trim();
        SearchMode searchMode = SearchMode.from(mode);
        int limit = topK == null || topK <= 0
                ? searchProperties.getTopK()
                : Math.min(topK, MAX_TOP_K);

        return BlockingCalls.call(() -> {
            long start = System.currentTimeMillis();
            List<SearchHit> hits = searchService.search(trimmed, limit, searchMode);
            long tookMillis = System.currentTimeMillis() - start;
            return new SearchResponse(
                    trimmed,
                    searchMode.name().toLowerCase(),
                    embeddingRegistry.defaultClient().providerName(),
                    embeddingRegistry.defaultClient().dimensions(),
                    tookMillis,
                    index.indexedCount(),
                    hits.stream().map(SearchHitResponse::from).toList());
        });
    }

    /** 换向量模型、或索引异常之后，用它按数据库里的块重建整个索引 */
    @PostMapping("/reindex")
    public Mono<ReindexResponse> reindex() {
        return BlockingCalls.call(() -> {
            IndexMaintenanceService.ReindexResult result = maintenanceService.rebuild();
            return new ReindexResponse(
                    result.documents(), result.chunks(), result.tookMillis(), result.index());
        });
    }
}
