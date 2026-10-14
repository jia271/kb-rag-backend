package com.example.rag.document.api;

import com.example.rag.document.api.dto.ChunkPageResponse;
import com.example.rag.document.api.dto.ChunkResponse;
import com.example.rag.document.api.dto.DocumentResponse;
import com.example.rag.document.parse.ParserRegistry;
import com.example.rag.document.service.DocumentIngestionService;
import com.example.rag.support.BlockingCalls;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/**
 * 文档管理接口。
 *
 * <p>WebFlux 的线程不能被阻塞，所有走 JPA 与文件系统的调用都包了一层
 * {@link BlockingCalls}，丢到弹性线程池执行。
 */
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private static final int MAX_PAGE_SIZE = 200;

    private final DocumentIngestionService ingestion;

    public DocumentController(DocumentIngestionService ingestion) {
        this.ingestion = ingestion;
    }

    /** 上传文档，立即返回登记结果，解析在后台进行 */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<DocumentResponse> upload(@RequestPart("file") Mono<FilePart> filePart) {
        return filePart.flatMap(this::storeAndAccept);
    }

    @GetMapping
    public Mono<List<DocumentResponse>> list() {
        return BlockingCalls.call(() -> ingestion.list().stream().map(DocumentResponse::from).toList());
    }

    @GetMapping("/{id}")
    public Mono<DocumentResponse> detail(@PathVariable Long id) {
        return BlockingCalls.call(() -> DocumentResponse.from(ingestion.get(id)));
    }

    /** 查看切分结果，用来检查分块质量——M1 阶段最常用的接口 */
    @GetMapping("/{id}/chunks")
    public Mono<ChunkPageResponse> chunks(@PathVariable Long id,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        int safePage = Math.max(0, page);
        return BlockingCalls.call(() -> {
            Page<com.example.rag.document.domain.DocumentChunk> result =
                    ingestion.chunks(id, PageRequest.of(safePage, safeSize));
            return new ChunkPageResponse(
                    result.getTotalElements(),
                    safePage,
                    safeSize,
                    result.getContent().stream().map(ChunkResponse::from).toList());
        });
    }

    @DeleteMapping("/{id}")
    public Mono<Void> delete(@PathVariable Long id) {
        return BlockingCalls.run(() -> ingestion.delete(id));
    }

    private Mono<DocumentResponse> storeAndAccept(FilePart part) {
        String originalFileName = sanitizeFileName(part.filename());
        String contentType = part.headers().getContentType() == null
                ? MediaType.APPLICATION_OCTET_STREAM_VALUE
                : part.headers().getContentType().toString();

        return BlockingCalls.call(ingestion::prepareStorageDirectory)
                .flatMap(directory -> {
                    Path target = directory.resolve(UUID.randomUUID() + "-" + originalFileName);
                    return part.transferTo(target)
                            .then(BlockingCalls.call(() -> sizeOf(target)))
                            .flatMap(size -> BlockingCalls.call(
                                    () -> ingestion.accept(originalFileName, contentType, target, size)))
                            .map(DocumentResponse::from);
                });
    }

    private long sizeOf(Path file) throws IOException {
        return Files.size(file);
    }

    /** 去掉路径成分，避免用户用 ../../ 这种文件名写到目录外面去 */
    private String sanitizeFileName(String fileName) {
        String name = fileName == null ? "unnamed" : fileName.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        if (ParserRegistry.extensionOf(name) == null) {
            throw new IllegalArgumentException("文件缺少扩展名，无法判断格式：" + name);
        }
        return name.replaceAll("[^A-Za-z0-9._\\-\\u4e00-\\u9fa5]", "_");
    }
}
