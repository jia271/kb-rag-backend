package com.example.rag.document.repository;

import com.example.rag.document.domain.DocumentChunk;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, Long> {

    Page<DocumentChunk> findByDocumentIdOrderByOrderIndex(Long documentId, Pageable pageable);

    long countByDocumentId(Long documentId);

    void deleteByDocumentId(Long documentId);
}
