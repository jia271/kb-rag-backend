package com.example.rag.document.service;

import com.example.rag.document.chunk.TextChunker;
import com.example.rag.document.domain.Document;
import com.example.rag.document.domain.DocumentChunk;
import com.example.rag.document.domain.DocumentStatus;
import com.example.rag.document.parse.DocumentParser;
import com.example.rag.document.parse.ParsedDocument;
import com.example.rag.document.parse.ParserRegistry;
import com.example.rag.document.repository.DocumentChunkRepository;
import com.example.rag.document.repository.DocumentRepository;
import com.example.rag.support.Errors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 真正的解析任务：读文件 → 解析成文本 → 切块 → 落库。
 *
 * <p>放在独立的 Bean 里，是为了让事务代理能生效（同一个类里自己调自己不会走代理）。
 *
 * <p>已知取舍：解析耗时较长时，整个事务期间都会占着一个数据库连接。
 * 文档量变大后应该拆成"置为 PARSING / 解析切分 / 写入结果"三段短事务。
 */
@Slf4j
@Service
public class DocumentProcessingService {

    private final DocumentRepository documents;
    private final DocumentChunkRepository chunks;
    private final ParserRegistry parsers;
    private final TextChunker chunker;

    public DocumentProcessingService(DocumentRepository documents,
                                     DocumentChunkRepository chunks,
                                     ParserRegistry parsers,
                                     TextChunker chunker) {
        this.documents = documents;
        this.chunks = chunks;
        this.parsers = parsers;
        this.chunker = chunker;
    }

    @Transactional
    public void process(Long documentId) {
        Document document = documents.findById(documentId).orElse(null);
        if (document == null) {
            log.warn("待解析的文档不存在：{}", documentId);
            return;
        }
        document.setStatus(DocumentStatus.PARSING);
        documents.save(document);

        try {
            byte[] bytes = Files.readAllBytes(Path.of(document.getStoragePath()));
            DocumentParser parser = parsers.find(document.getFileName())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "不支持的文件类型：" + document.getFileName()
                                    + "，当前支持 " + parsers.supportedExtensions()));

            ParsedDocument parsed = parser.parse(bytes, document.getFileName());
            List<TextChunker.TextChunk> textChunks = chunker.split(parsed);
            if (textChunks.isEmpty()) {
                throw new IllegalStateException("文档解析后没有任何可用的文本内容");
            }

            chunks.deleteByDocumentId(documentId);
            chunks.saveAll(toEntities(documentId, textChunks));

            document.setChunkCount(textChunks.size());
            document.setStatus(DocumentStatus.READY);
            document.setParsedAt(Instant.now());
            document.setFailureReason(null);
            log.info("文档解析完成：{}（{} 段 -> {} 块，共 {} 字）",
                    document.getFileName(), parsed.segments().size(),
                    textChunks.size(), parsed.totalChars());
        } catch (Exception error) {
            document.setStatus(DocumentStatus.FAILED);
            document.setFailureReason(truncate(Errors.rootMessage(error)));
            log.warn("文档解析失败：{}，原因：{}", document.getFileName(), document.getFailureReason());
        }
        documents.save(document);
    }

    private List<DocumentChunk> toEntities(Long documentId, List<TextChunker.TextChunk> textChunks) {
        List<DocumentChunk> entities = new ArrayList<>(textChunks.size());
        for (int index = 0; index < textChunks.size(); index++) {
            TextChunker.TextChunk textChunk = textChunks.get(index);
            DocumentChunk entity = new DocumentChunk();
            entity.setDocumentId(documentId);
            entity.setOrderIndex(index);
            entity.setContent(textChunk.content());
            entity.setCharCount(textChunk.content().length());
            entity.setPageNumber(textChunk.pageNumber());
            entity.setHeading(textChunk.heading());
            entities.add(entity);
        }
        return entities;
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= 500 ? message : message.substring(0, 500);
    }
}
