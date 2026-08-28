package com.neaz.travel.visa.infrastructure.persistence;

import com.neaz.travel.visa.domain.model.SourceCheckLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data repository for {@link SourceCheckLogJpaEntity}.
 * {@code findByStatusAndReviewedFalse} backs {@code findPendingReview()},
 * matching the {@code idx_source_check_log_pending} partial index in V9.
 */
public interface SourceCheckLogJpaRepository extends JpaRepository<SourceCheckLogJpaEntity, UUID> {

    List<SourceCheckLogJpaEntity> findByStatusAndReviewedFalse(SourceCheckLog.Status status);
}
