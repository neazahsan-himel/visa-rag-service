package com.neaz.travel.visa.domain.repository;

import com.neaz.travel.visa.domain.model.VisaAnswer;
import com.neaz.travel.visa.domain.model.VisaQuery;

import java.util.List;
import java.util.UUID;

/**
 * Append-only audit log for processed queries, implemented by
 * {@code infrastructure/persistence} against the {@code query_audit} table.
 */
public interface VisaQueryLogRepository {

    /**
     * Records a completed query for audit purposes.
     *
     * @param query             the query that was processed
     * @param answer            the answer that was returned
     * @param retrievedChunkIds identifiers of every chunk used to produce the answer
     * @param modelId           identifier of the LLM that generated the answer
     * @param latencyMs         end-to-end processing time in milliseconds
     */
    void log(VisaQuery query, VisaAnswer answer, List<UUID> retrievedChunkIds, String modelId, long latencyMs);
}