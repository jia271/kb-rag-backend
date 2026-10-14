package com.example.rag.document.service;

import com.example.rag.document.domain.Document;
import com.example.rag.document.domain.DocumentChunk;
import com.example.rag.document.domain.DocumentStatus;
import com.example.rag.document.repository.DocumentChunkRepository;
import com.example.rag.document.repository.DocumentRepository;
import com.example.rag.retrieval.ChunkIndexService;
import com.example.rag.support.Errors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * 把落库后的块向量化并写进索引。
 *
 * <p>刻意放在事务之外：向量化要走一次外部接口，把它塞进数据库事务里会让事务持有连接的时间
 * 被网络延迟撑长，是不必要的代价。
 *
 * <p>索引失败不会把文档状态改成 FAILED——文本已经成功入库，只是暂时搜不到，
 * 修好向量化配置后调一次重建接口就能补上。
 */
@Slf4j
@Service
public class DocumentIndexingService {

    private final DocumentRepository documents;
    private final DocumentChunkRepository chunks;
    private final ChunkIndexService index;

    public DocumentIndexingService(DocumentRepository documents,
                                   DocumentChunkRepository chunks,
                                   ChunkIndexService index) {
        this.documents = documents;
        this.chunks = chunks;
        this.index = index;
    }

    public void index(Long documentId) {
        Document document = documents.findById(documentId).orElse(null);
        if (document == null) {
            return;
        }
        if (document.getStatus() != DocumentStatus.READY) {
            log.debug("文档 {} 当前状态为 {}，跳过索引", document.getFileName(), document.getStatus());
            return;
        }
        try {
            List<DocumentChunk> documentChunks = chunks
                    .findByDocumentIdOrderByOrderIndex(documentId, PageRequest.of(0, Integer.MAX_VALUE))
                    .getContent();
            int indexed = index.indexDocument(documentId, document.getFileName(), documentChunks);
            document.setIndexedAt(Instant.now());
            documents.save(document);
            log.info("文档已进入索引：{}（{} 个块）", document.getFileName(), indexed);
        } catch (Exception error) {
            log.warn("文档索引失败：{}，原因：{}。文本已入库，可修复后调用 POST /api/search/reindex 重建",
                    document.getFileName(), Errors.rootMessage(error));
        }
    }
}
