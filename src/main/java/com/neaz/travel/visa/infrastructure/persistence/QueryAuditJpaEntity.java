package com.neaz.travel.visa.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA mapping for the append-only {@code query_audit} table. Unlike {@link
 * DocumentJpaEntity} and {@link ChunkJpaEntity}, {@code id} is generated
 * here rather than app-supplied, since {@code VisaQueryLogRepository.log(...)}
 * returns {@code void} and no caller ever needs the identifier.
 */
@Entity
@Table(name = "query_audit")
public class QueryAuditJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "query_text", nullable = false)
    private String queryText;

    @Column(name = "passport_iso", nullable = false, length = 2)
    private String passportIso;

    @Column(name = "destination_iso", nullable = false, length = 2)
    private String destinationIso;

    @Column(name = "visa_type_code", length = 50)
    private String visaTypeCode;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "retrieved_chunk_ids", nullable = false)
    private UUID[] retrievedChunkIds;

    @Column(name = "confidence_score", nullable = false, precision = 4, scale = 3)
    private BigDecimal confidenceScore;

    @Column(name = "answer_text", nullable = false)
    private String answerText;

    @Column(name = "model_id", nullable = false, length = 100)
    private String modelId;

    @Column(name = "latency_ms", nullable = false)
    private int latencyMs;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    protected QueryAuditJpaEntity() {
        // required by JPA
    }

    public QueryAuditJpaEntity(String queryText, String passportIso, String destinationIso, String visaTypeCode,
                                UUID[] retrievedChunkIds, BigDecimal confidenceScore, String answerText,
                                String modelId, int latencyMs) {
        this.queryText = queryText;
        this.passportIso = passportIso;
        this.destinationIso = destinationIso;
        this.visaTypeCode = visaTypeCode;
        this.retrievedChunkIds = retrievedChunkIds;
        this.confidenceScore = confidenceScore;
        this.answerText = answerText;
        this.modelId = modelId;
        this.latencyMs = latencyMs;
    }

    public UUID getId() {
        return id;
    }

    public String getQueryText() {
        return queryText;
    }

    public String getPassportIso() {
        return passportIso;
    }

    public String getDestinationIso() {
        return destinationIso;
    }

    public String getVisaTypeCode() {
        return visaTypeCode;
    }

    public UUID[] getRetrievedChunkIds() {
        return retrievedChunkIds;
    }

    public BigDecimal getConfidenceScore() {
        return confidenceScore;
    }

    public String getAnswerText() {
        return answerText;
    }

    public String getModelId() {
        return modelId;
    }

    public int getLatencyMs() {
        return latencyMs;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}