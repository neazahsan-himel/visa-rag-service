package com.neaz.travel.visa.infrastructure.persistence;

import com.neaz.travel.visa.domain.model.Chunk;
import com.neaz.travel.visa.domain.model.Document;
import com.neaz.travel.visa.domain.model.DocumentMetadata;
import com.neaz.travel.visa.domain.model.MetadataFilter;
import com.neaz.travel.visa.domain.model.ScoredChunk;
import com.neaz.travel.visa.domain.repository.DocumentRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * {@link DocumentRepository} implementation backed by PostgreSQL via Spring
 * Data JPA. Bridges the app-supplied ISO/code strings on {@link
 * DocumentMetadata} to the numeric {@code country_id}/{@code visa_type_id}
 * foreign keys on {@link DocumentJpaEntity}, resolving both directions
 * against the Flyway-seeded {@code countries}/{@code visa_types} reference
 * tables.
 */
@Repository
public class DocumentRepositoryAdapter implements DocumentRepository {

    private final DocumentJpaRepository documentJpaRepository;
    private final ChunkJpaRepository chunkJpaRepository;
    private final CountryJpaRepository countryJpaRepository;
    private final VisaTypeJpaRepository visaTypeJpaRepository;

    public DocumentRepositoryAdapter(DocumentJpaRepository documentJpaRepository,
                                      ChunkJpaRepository chunkJpaRepository,
                                      CountryJpaRepository countryJpaRepository,
                                      VisaTypeJpaRepository visaTypeJpaRepository) {
        this.documentJpaRepository = documentJpaRepository;
        this.chunkJpaRepository = chunkJpaRepository;
        this.countryJpaRepository = countryJpaRepository;
        this.visaTypeJpaRepository = visaTypeJpaRepository;
    }

    @Override
    public Document save(Document document) {
        documentJpaRepository.save(toEntity(document));

        List<ChunkJpaEntity> chunkEntities = document.getChunks().stream()
                .map(chunk -> new ChunkJpaEntity(
                        chunk.getId(),
                        chunk.getDocumentId(),
                        chunk.getChunkIndex(),
                        chunk.getContent(),
                        chunk.getTokenCount(),
                        chunk.getQdrantPointId()))
                .toList();
        chunkJpaRepository.saveAll(chunkEntities);

        return document;
    }

    /**
     * {@inheritDoc}
     *
     * <p>The deactivation is flushed explicitly before the replacement is
     * saved: Hibernate otherwise orders inserts ahead of updates at flush
     * time, which would briefly leave two active rows for the same key and
     * violate {@code idx_documents_active_key}.
     */
    @Override
    @Transactional
    public Document replace(Document superseded, Document replacement) {
        documentJpaRepository.saveAndFlush(toEntity(superseded));
        return save(replacement);
    }

    @Override
    public Optional<Document> findById(UUID id) {
        return documentJpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<Document> findByFileHash(String fileHash) {
        return documentJpaRepository.findByFileHash(fileHash).map(this::toDomain);
    }

    @Override
    public List<Document> findAllActiveWithSourceUrl() {
        return documentJpaRepository.findByActiveTrueAndSourceUrlIsNotNull().stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public Optional<Document> findActiveByDocumentKey(String documentKey) {
        return documentJpaRepository.findByActiveTrueAndDocumentKey(documentKey).map(this::toDomain);
    }

    @Override
    public List<ScoredChunk> fullTextSearch(String queryText, MetadataFilter filter, int topK) {
        return chunkJpaRepository.searchFullText(
                        queryText, filter.passportIso(), filter.destinationIso(), filter.visaTypeCode(), topK)
                .stream()
                .map(row -> new ScoredChunk(
                        new Chunk(row.getId(), row.getDocumentId(), row.getChunkIndex(), row.getContent(),
                                row.getTokenCount(), row.getQdrantPointId()),
                        row.getRank(),
                        row.getLastUpdated()))
                .toList();
    }

    private DocumentJpaEntity toEntity(Document document) {
        DocumentMetadata metadata = document.getMetadata();
        Long countryId = resolveCountryId(metadata.destinationIso());
        Long visaTypeId = resolveVisaTypeId(countryId, metadata.visaTypeCode());

        return new DocumentJpaEntity(
                document.getId(),
                metadata.documentKey(),
                countryId,
                visaTypeId,
                metadata.passportIso(),
                document.getTitle(),
                metadata.sourceUrl(),
                document.getFileHash(),
                metadata.lastUpdated(),
                metadata.version(),
                document.getIngestedAt(),
                document.isActive());
    }

    private Document toDomain(DocumentJpaEntity entity) {
        String destinationIso = resolveDestinationIso(entity.getCountryId());
        String visaTypeCode = resolveVisaTypeCode(entity.getVisaTypeId());
        DocumentMetadata metadata = new DocumentMetadata(
                entity.getDocumentKey(),
                destinationIso,
                entity.getPassportIso(),
                visaTypeCode,
                entity.getSourceUrl(),
                entity.getLastUpdated(),
                entity.getVersion());

        List<Chunk> chunks = chunkJpaRepository.findByDocumentIdOrderByChunkIndexAsc(entity.getId()).stream()
                .map(c -> new Chunk(c.getId(), c.getDocumentId(), c.getChunkIndex(), c.getContent(),
                        c.getTokenCount(), c.getQdrantPointId()))
                .toList();

        return new Document(entity.getId(), entity.getTitle(), entity.getFileHash(), metadata, chunks,
                entity.getIngestedAt(), entity.isActive());
    }

    private Long resolveCountryId(String isoCode) {
        return countryJpaRepository.findByIsoCode(isoCode)
                .map(CountryJpaEntity::getId)
                .orElseThrow(() -> new IllegalStateException(
                        "No seeded country reference data for ISO code '%s'".formatted(isoCode)));
    }

    private Long resolveVisaTypeId(Long countryId, String code) {
        return visaTypeJpaRepository.findByCountryIdAndCode(countryId, code)
                .map(VisaTypeJpaEntity::getId)
                .orElseThrow(() -> new IllegalStateException(
                        "No seeded visa type reference data for code '%s' under country id %d"
                                .formatted(code, countryId)));
    }

    private String resolveDestinationIso(Long countryId) {
        return countryJpaRepository.findById(countryId)
                .map(CountryJpaEntity::getIsoCode)
                .orElseThrow(() -> new IllegalStateException(
                        "Referenced country id %d no longer exists".formatted(countryId)));
    }

    private String resolveVisaTypeCode(Long visaTypeId) {
        return visaTypeJpaRepository.findById(visaTypeId)
                .map(VisaTypeJpaEntity::getCode)
                .orElseThrow(() -> new IllegalStateException(
                        "Referenced visa type id %d no longer exists".formatted(visaTypeId)));
    }
}