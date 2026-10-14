package com.example.rag.retrieval;

import com.example.rag.document.domain.DocumentChunk;
import com.example.rag.document.repository.DocumentChunkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class HybridSearchServiceTest {

    private static final Long TEST_DOCUMENT_ID = 9001L;
    private static final String TEST_FILE_NAME = "测试文档.md";

    @Autowired
    private ChunkIndexService index;

    @Autowired
    private HybridSearchService search;

    @Autowired
    private DocumentChunkRepository chunks;

    private DocumentChunk reimbursementChunk;

    @BeforeEach
    void setUp() throws IOException {
        List<DocumentChunk> saved = chunks.saveAll(List.of(
                chunk(0, "混合检索同时使用关键词和向量两种方式，取长补短。"),
                chunk(1, "公司的报销流程需要先在系统里提交申请，再由主管审批。"),
                chunk(2, "向量检索理解语义，能处理换一种说法的提问。")));
        reimbursementChunk = saved.get(1);
        index.indexDocument(TEST_DOCUMENT_ID, TEST_FILE_NAME, saved);
    }

    @Test
    void keywordSearchPrefersExactTermMatch() throws IOException {
        List<SearchHit> hits = search.search("报销流程", 3, SearchMode.KEYWORD);

        assertThat(hits).isNotEmpty();
        assertThat(hits.get(0).chunkId()).isEqualTo(reimbursementChunk.getId());
        assertThat(hits.get(0).keywordRank()).isEqualTo(1);
        assertThat(hits.get(0).vectorRank()).isNull();
    }

    @Test
    void vectorSearchReturnsResults() throws IOException {
        List<SearchHit> hits = search.search("语义检索方式", 3, SearchMode.VECTOR);

        assertThat(hits).isNotEmpty();
        assertThat(hits.get(0).vectorRank()).isEqualTo(1);
        assertThat(hits.get(0).keywordRank()).isNull();
    }

    @Test
    void hybridSearchFusesBothPathsWithoutDuplicates() throws IOException {
        List<SearchHit> hits = search.search("报销流程", 3, SearchMode.HYBRID);

        assertThat(hits).isNotEmpty();
        assertThat(hits).extracting(SearchHit::chunkId).doesNotHaveDuplicates();
        assertThat(hits).extracting(SearchHit::chunkId).contains(reimbursementChunk.getId());
        // 融合后名次字段保留，方便解释"为什么排在前面"
        assertThat(hits.get(0).score()).isGreaterThan(0);
    }

    @Test
    void reindexingSameDocumentDoesNotDuplicateChunks() throws IOException {
        // 先只留下一条，再重新索引一次：如果"先删后加"没生效，检索结果会出现两条同样的块
        index.indexDocument(TEST_DOCUMENT_ID, TEST_FILE_NAME, List.of(reimbursementChunk));

        List<SearchHit> hits = search.search("报销流程", 10, SearchMode.KEYWORD);

        assertThat(hits).extracting(SearchHit::chunkId).doesNotHaveDuplicates();
        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).chunkId()).isEqualTo(reimbursementChunk.getId());
    }

    @Test
    void deletingDocumentRemovesItFromIndex() throws IOException {
        assertThat(search.search("报销流程", 3, SearchMode.KEYWORD)).isNotEmpty();

        index.deleteDocument(TEST_DOCUMENT_ID);

        assertThat(search.search("报销流程", 3, SearchMode.KEYWORD)).isEmpty();
    }

    private DocumentChunk chunk(int orderIndex, String content) {
        DocumentChunk chunk = new DocumentChunk();
        chunk.setDocumentId(TEST_DOCUMENT_ID);
        chunk.setOrderIndex(orderIndex);
        chunk.setContent(content);
        chunk.setCharCount(content.length());
        return chunk;
    }
}
