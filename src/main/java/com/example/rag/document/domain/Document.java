package com.example.rag.document.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "documents")
@Getter
@Setter
@NoArgsConstructor
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String fileName;

    private String contentType;

    @Column(nullable = false)
    private long sizeBytes;

    /** 文件内容指纹，用来识别重复上传 */
    @Column(length = 64)
    private String sha256;

    @Column(nullable = false, length = 512)
    private String storagePath;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DocumentStatus status = DocumentStatus.PENDING;

    @Column(nullable = false)
    private int chunkCount;

    @Column(length = 512)
    private String failureReason;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    private Instant parsedAt;

    /** 进入检索索引的时间；为空说明文本已入库但还没被索引，搜不到 */
    private Instant indexedAt;
}
