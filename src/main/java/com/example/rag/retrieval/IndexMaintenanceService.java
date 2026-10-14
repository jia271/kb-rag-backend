package com.example.rag.retrieval;

import com.example.rag.document.domain.Document;
import com.example.rag.document.domain.DocumentChunk;
import com.example.rag.document.domain.DocumentStatus;
import com.example.rag.document.repository.DocumentChunkRepository;
import com.example.rag.document.repository.DocumentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

/**
 * 索引维护：把数据库里已有的块重新向量化并写入索引。
 *
 * <p>换向量模型之后必然要重建一次——新模型算出的向量和旧索引里的向量维度或语义空间都不一样，
 * 混用只会得到错误的排序。
 */
@Slf4j
@Service
public class IndexMaintenanceService {

    private static final int PAGE_SIZE = 200;

    private final DocumentRepository documents;
    private final DocumentChunkRepository chunks;
    private final ChunkIndexService index;

    public IndexMaintenanceService(DocumentRepository documents,
                                   DocumentChunkRepository chunks,
                                   ChunkIndexService index) {
        this.documents = documents;
        this.chunks = chunks;
        this.index = index;
    }

    public ReindexResult rebuild() throws IOException {
        long start = System.currentTimeMillis();
        index.deleteAll();

        int indexedDocuments = 0;
        int indexedChunks = 0;
        for (Document document : documents.findAll()) {
            if (document.getStatus() != DocumentStatus.READY) {
                continue;
            }
            List<DocumentChunk> documentChunks = chunks
                    .findByDocumentIdOrderByOrderIndex(document.getId(), PageRequest.of(0, Integer.MAX_VALUE))
                    .getContent();
            indexedChunks += index.indexDocument(document.getId(), document.getFileName(), documentChunks);
            indexedDocuments++;
        }

        long tookMillis = System.currentTimeMillis() - start;
        log.info("索引重建完成：{} 份文档、{} 个块，用时 {} ms", indexedDocuments, indexedChunks, tookMillis);
        return new ReindexResult(indexedDocuments, indexedChunks, tookMillis, index.describe());
    }

    public record ReindexResult(int documents, int chunks, long tookMillis, String index) {
    }
}
