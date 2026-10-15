package com.example.rag.usage;

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
 * 一次模型调用的用量记录。
 *
 * <p>失败的调用也要记：失败同样消耗时间和重试成本，只统计成功的会看不清真实代价。
 */
@Entity
@Table(name = "llm_usage", indexes = {
        @Index(name = "idx_usage_created", columnList = "createdAt"),
        @Index(name = "idx_usage_session", columnList = "sessionId")})
@Getter
@Setter
@NoArgsConstructor
public class LlmUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 64)
    private String traceId;

    @Column(length = 64)
    private String sessionId;

    /** 调用场景：chat / stream */
    @Column(nullable = false, length = 32)
    private String scene;

    @Column(nullable = false, length = 32)
    private String provider;

    @Column(nullable = false, length = 64)
    private String model;

    @Column(nullable = false)
    private int promptTokens;

    @Column(nullable = false)
    private int completionTokens;

    @Column(nullable = false)
    private long latencyMillis;

    @Column(nullable = false)
    private boolean success;

    @Column(length = 512)
    private String failureReason;

    /** 按配置单价算出的金额，单位：元 */
    @Column(nullable = false)
    private double cost;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();
}
