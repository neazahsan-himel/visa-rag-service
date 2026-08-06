package com.neaz.travel.visa.infrastructure.persistence;

import com.neaz.travel.visa.domain.model.MetadataFilter;
import com.neaz.travel.visa.domain.model.VisaAnswer;
import com.neaz.travel.visa.domain.model.VisaQuery;
import com.neaz.travel.visa.domain.repository.VisaQueryLogRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

/**
 * {@link VisaQueryLogRepository} implementation backed by the append-only
 * {@code query_audit} table via Spring Data JPA.
 */
@Repository
public class VisaQueryLogRepositoryAdapter implements VisaQueryLogRepository {

    private final QueryAuditJpaRepository queryAuditJpaRepository;

    public VisaQueryLogRepositoryAdapter(QueryAuditJpaRepository queryAuditJpaRepository) {
        this.queryAuditJpaRepository = queryAuditJpaRepository;
    }

    @Override
    public void log(VisaQuery query, VisaAnswer answer, List<UUID> retrievedChunkIds, String modelId,
                     long latencyMs) {
        MetadataFilter filter = query.filter();
        QueryAuditJpaEntity entity = new QueryAuditJpaEntity(
                query.queryText(),
                filter.passportIso(),
                filter.destinationIso(),
                filter.visaTypeCode(),
                retrievedChunkIds.toArray(UUID[]::new),
                BigDecimal.valueOf(answer.confidenceScore()).setScale(3, RoundingMode.HALF_UP),
                answer.answerText(),
                modelId,
                (int) latencyMs);
        queryAuditJpaRepository.save(entity);
    }
}