package com.example.rag.document.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * 文档切分后的片段。pageNumber / heading 是"来源定位"，
 * 将来回答问题时要把答案指回原文的哪一页、哪一节。
 */
@Entity
@Table(name = "document_chunks", indexes = @Index(name = "idx_chunk_document", columnList = "documentId"))
@Getter
@Setter
@NoArgsConstructor
public class DocumentChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long documentId;

    /** 在文档内的顺序，从 0 开始 */
    @Column(nullable = false)
    private int orderIndex;

    @Column(nullable = false, length = 10000)
    private String content;

    @Column(nullable = false)
    private int charCount;

    /** 来源页码，仅 PDF 有 */
    private Integer pageNumber;

    /** 来源标题路径，仅 Markdown 有 */
    @Column(length = 512)
    private String heading;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();
}
