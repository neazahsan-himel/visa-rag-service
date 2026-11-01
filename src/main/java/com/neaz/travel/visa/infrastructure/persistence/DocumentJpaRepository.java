package com.neaz.travel.visa.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data repository for {@link DocumentJpaEntity}. {@code findById}
 * and {@code save} are inherited from {@link JpaRepository}; the file-hash
 * lookup backs ingestion dedup, the active/source-url lookup backs the
 * source-change monitor, and the active/document-key lookup backs version
 * supersession.
 */
public interface DocumentJpaRepository extends JpaRepository<DocumentJpaEntity, UUID> {

    Optional<DocumentJpaEntity> findByFileHash(String fileHash);

    List<DocumentJpaEntity> findByActiveTrueAndSourceUrlIsNotNull();

    Optional<DocumentJpaEntity> findByActiveTrueAndDocumentKey(String documentKey);
}