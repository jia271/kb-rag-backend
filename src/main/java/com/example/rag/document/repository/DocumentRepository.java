package com.example.rag.document.repository;

import com.example.rag.document.domain.Document;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DocumentRepository extends JpaRepository<Document, Long> {

    Optional<Document> findBySha256(String sha256);

    List<Document> findAllByOrderByCreatedAtDesc();
}
