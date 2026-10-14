package com.example.rag.document.service;

import com.example.rag.document.domain.Document;
import com.example.rag.document.domain.DocumentChunk;
import com.example.rag.document.domain.DocumentStatus;
import com.example.rag.document.repository.DocumentChunkRepository;
import com.example.rag.document.repository.DocumentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class DocumentIngestionServiceTest {

    @Autowired
    private DocumentIngestionService ingestion;

    @Autowired
    private DocumentRepository documents;

    @Autowired
    private DocumentChunkRepository chunks;

    @Test
    void uploadedDocumentIsParsedAndChunkedAsynchronously() throws Exception {
        String markdown = """
                # 产品说明

                本系统用于把企业文档变成可检索的知识库。用户上传文档后，系统会解析并切分成片段。

                ## 检索

                检索阶段会同时使用关键词和向量两种方式，取长补短。
                """;
        Path file = writeSample("说明.md", markdown);

        Document accepted = ingestion.accept("说明.md", "text/markdown", file, Files.size(file));

        assertThat(accepted.getStatus()).isEqualTo(DocumentStatus.PENDING);
        Document processed = awaitFinished(accepted.getId());
        assertThat(processed.getStatus()).isEqualTo(DocumentStatus.READY);
        assertThat(processed.getFailureReason()).isNull();

        List<DocumentChunk> stored = chunks
                .findByDocumentIdOrderByOrderIndex(processed.getId(), PageRequest.of(0, 50))
                .getContent();
        assertThat(stored).isNotEmpty();
        assertThat(processed.getChunkCount()).isEqualTo(stored.size());
        assertThat(stored.get(0).getHeading()).isEqualTo("产品说明");
        assertThat(stored.get(0).getCharCount()).isEqualTo(stored.get(0).getContent().length());
    }

    @Test
    void sameContentIsNotProcessedTwice() throws Exception {
        byte[] content = "# 重复文件\n\n这份内容会被上传两次。".getBytes(StandardCharsets.UTF_8);
        Path first = writeSample("重复-1.md", content);
        Path second = writeSample("重复-2.md", content);

        Document firstDocument = ingestion.accept("重复-1.md", "text/markdown", first, Files.size(first));
        Document secondDocument = ingestion.accept("重复-2.md", "text/markdown", second, Files.size(second));

        assertThat(secondDocument.getId()).isEqualTo(firstDocument.getId());
        // 重复的那份临时文件应该已经被清掉
        assertThat(Files.exists(second)).isFalse();
    }

    @Test
    void unsupportedFileTypeIsRejected() throws Exception {
        Path file = writeSample("说明.docx", "不是支持的类型");

        assertThat(org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> ingestion.accept("说明.docx", "application/msword", file, Files.size(file)))
                .getMessage())
                .contains("不支持的文件类型");
    }

    private Path writeSample(String fileName, String content) throws Exception {
        return writeSample(fileName, content.getBytes(StandardCharsets.UTF_8));
    }

    private Path writeSample(String fileName, byte[] content) throws Exception {
        Path directory = ingestion.prepareStorageDirectory();
        Path file = directory.resolve(UUID.randomUUID() + "-" + fileName);
        Files.write(file, content);
        return file;
    }

    private Document awaitFinished(Long documentId) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 15_000L;
        while (System.currentTimeMillis() < deadline) {
            Document document = documents.findById(documentId).orElseThrow();
            if (document.getStatus() == DocumentStatus.READY || document.getStatus() == DocumentStatus.FAILED) {
                return document;
            }
            Thread.sleep(50L);
        }
        throw new AssertionError("等待文档解析完成超时：" + documentId);
    }
}
