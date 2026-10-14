package com.example.rag.document.service;

import com.example.rag.config.IngestionExecutorConfig;
import com.example.rag.config.IngestionProperties;
import com.example.rag.document.domain.Document;
import com.example.rag.document.domain.DocumentChunk;
import com.example.rag.document.domain.DocumentStatus;
import com.example.rag.document.parse.ParserRegistry;
import com.example.rag.document.repository.DocumentChunkRepository;
import com.example.rag.document.repository.DocumentRepository;
import com.example.rag.retrieval.ChunkIndexService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * 文档接收与生命周期管理：落盘、去重、登记、派发解析任务、查询与删除。
 */
@Slf4j
@Service
public class DocumentIngestionService {

    private final DocumentRepository documents;
    private final DocumentChunkRepository chunks;
    private final DocumentProcessingService processingService;
    private final DocumentIndexingService indexingService;
    private final ChunkIndexService chunkIndex;
    private final IngestionProperties ingestionProperties;
    private final ParserRegistry parsers;
    private final Executor ingestionExecutor;

    public DocumentIngestionService(DocumentRepository documents,
                                    DocumentChunkRepository chunks,
                                    DocumentProcessingService processingService,
                                    DocumentIndexingService indexingService,
                                    ChunkIndexService chunkIndex,
                                    IngestionProperties ingestionProperties,
                                    ParserRegistry parsers,
                                    @Qualifier(IngestionExecutorConfig.INGESTION_EXECUTOR) Executor ingestionExecutor) {
        this.documents = documents;
        this.chunks = chunks;
        this.processingService = processingService;
        this.indexingService = indexingService;
        this.chunkIndex = chunkIndex;
        this.ingestionProperties = ingestionProperties;
        this.parsers = parsers;
        this.ingestionExecutor = ingestionExecutor;
    }

    public Path prepareStorageDirectory() throws IOException {
        Path directory = Path.of(ingestionProperties.getStorageDir()).toAbsolutePath().normalize();
        Files.createDirectories(directory);
        return directory;
    }

    /**
     * 登记一个已经落盘的文件，并异步派发解析任务。
     *
     * <p>刻意不加 {@code @Transactional}：解析任务要等登记数据真正提交后才能读到它，
     * 事务没提交就派发任务，异步线程会查不到这条记录。
     */
    public Document accept(String originalFileName, String contentType, Path storedFile, long sizeBytes)
            throws IOException {

        if (parsers.find(originalFileName).isEmpty()) {
            Files.deleteIfExists(storedFile);
            throw new IllegalArgumentException("不支持的文件类型：" + originalFileName
                    + "，当前支持 " + parsers.supportedExtensions());
        }

        String sha256 = sha256Of(storedFile);
        Optional<Document> duplicate = documents.findBySha256(sha256);
        if (duplicate.isPresent()) {
            Files.deleteIfExists(storedFile);
            log.info("文件内容与已有文档 {} 完全相同，跳过重复解析：{}", duplicate.get().getId(), originalFileName);
            return duplicate.get();
        }

        Document document = new Document();
        document.setFileName(originalFileName);
        document.setContentType(contentType);
        document.setSizeBytes(sizeBytes);
        document.setSha256(sha256);
        document.setStoragePath(storedFile.toString());
        document.setStatus(DocumentStatus.PENDING);
        documents.save(document);

        Long documentId = document.getId();
        ingestionExecutor.execute(() -> {
            // 先落库（短事务），再向量化写索引（涉及外部调用，放在事务外）
            processingService.process(documentId);
            indexingService.index(documentId);
        });
        log.info("已接收文档 {}（id={}，{} 字节），解析任务已派发", originalFileName, documentId, sizeBytes);
        return document;
    }

    public List<Document> list() {
        return documents.findAllByOrderByCreatedAtDesc();
    }

    public Document get(Long documentId) {
        return documents.findById(documentId)
                .orElseThrow(() -> new NoSuchElementException("文档不存在：" + documentId));
    }

    public Page<DocumentChunk> chunks(Long documentId, Pageable pageable) {
        // 先确认文档存在，避免查一个不存在的 id 返回空列表让人困惑
        get(documentId);
        return chunks.findByDocumentIdOrderByOrderIndex(documentId, pageable);
    }

    @Transactional
    public void delete(Long documentId) {
        Document document = get(documentId);
        chunks.deleteByDocumentId(documentId);
        documents.delete(document);
        try {
            Files.deleteIfExists(Path.of(document.getStoragePath()));
        } catch (IOException error) {
            log.warn("删除原始文件失败：{}，{}", document.getStoragePath(), error.toString());
        }
        try {
            chunkIndex.deleteDocument(documentId);
        } catch (IOException error) {
            log.warn("从索引中删除文档失败：{}", error.toString());
        }
        log.info("已删除文档 {}（id={}）", document.getFileName(), documentId);
    }

    private String sha256Of(Path file) throws IOException {
        try (InputStream input = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("当前 JVM 不支持 SHA-256", error);
        }
    }
}
