package com.neaz.travel.visa.domain.repository;

import com.neaz.travel.visa.domain.model.Document;
import com.neaz.travel.visa.domain.model.MetadataFilter;
import com.neaz.travel.visa.domain.model.ScoredChunk;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository contract for {@link Document} aggregates, implemented by
 * {@code infrastructure/persistence} against PostgreSQL. Also exposes the
 * full-text-search half of hybrid retrieval, since it is a metadata-filtered,
 * {@code ts_rank}-scored query over the same persisted chunk data.
 */
public interface DocumentRepository {

    Document save(Document document);

    Optional<Document> findById(UUID id);

    Optional<Document> findByFileHash(String fileHash);

    /**
     * Finds every active document that has an official {@code sourceUrl},
     * i.e. every document the source-change monitor can check.
     *
     * @return active documents with a checkable source, implementation-defined order
     */
    List<Document> findAllActiveWithSourceUrl();

    /**
     * Finds the currently active document for a given source URL, if any.
     * Used by ingestion to detect and supersede a prior version when
     * re-ingesting the same source with different content.
     *
     * @param sourceUrl the official source URL to look up
     * @return the active document for that source, if one exists
     */
    Optional<Document> findActiveBySourceUrl(String sourceUrl);

    /**
     * Performs a metadata-filtered full-text search over persisted chunks.
     * The filter must be applied before {@code ts_rank} scoring — an
     * unfiltered search is a defect.
     *
     * @param queryText the raw query text to search for
     * @param filter    the passport/destination/visa-type scope to apply before scoring
     * @param topK      maximum number of results to return
     * @return chunks ranked by keyword relevance, highest first
     */
    List<ScoredChunk> fullTextSearch(String queryText, MetadataFilter filter, int topK);
}