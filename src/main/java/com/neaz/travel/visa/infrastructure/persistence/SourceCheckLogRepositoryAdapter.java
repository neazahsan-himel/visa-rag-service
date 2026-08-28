package com.neaz.travel.visa.infrastructure.persistence;

import com.neaz.travel.visa.common.exception.VisaRagException;
import com.neaz.travel.visa.domain.model.SourceCheckLog;
import com.neaz.travel.visa.domain.repository.SourceCheckLogRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * {@link SourceCheckLogRepository} implementation backed by the
 * {@code source_check_log} table via Spring Data JPA.
 */
@Repository
public class SourceCheckLogRepositoryAdapter implements SourceCheckLogRepository {

    private final SourceCheckLogJpaRepository sourceCheckLogJpaRepository;

    public SourceCheckLogRepositoryAdapter(SourceCheckLogJpaRepository sourceCheckLogJpaRepository) {
        this.sourceCheckLogJpaRepository = sourceCheckLogJpaRepository;
    }

    @Override
    public void log(UUID documentId, String detectedHash, SourceCheckLog.Status status) {
        sourceCheckLogJpaRepository.save(new SourceCheckLogJpaEntity(documentId, detectedHash, status));
    }

    @Override
    public List<SourceCheckLog> findPendingReview() {
        return sourceCheckLogJpaRepository.findByStatusAndReviewedFalse(SourceCheckLog.Status.CHANGED).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public void markReviewed(UUID id) {
        SourceCheckLogJpaEntity entity = sourceCheckLogJpaRepository.findById(id)
                .orElseThrow(() -> new VisaRagException("Source check log not found: " + id));
        entity.setReviewed(true);
        sourceCheckLogJpaRepository.save(entity);
    }

    private SourceCheckLog toDomain(SourceCheckLogJpaEntity entity) {
        return new SourceCheckLog(
                entity.getId(),
                entity.getDocumentId(),
                entity.getCheckedAt(),
                entity.getDetectedHash(),
                entity.getStatus(),
                entity.isReviewed());
    }
}
