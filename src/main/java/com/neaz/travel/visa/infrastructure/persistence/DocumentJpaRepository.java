package com.neaz.travel.visa.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data repository for {@link DocumentJpaEntity}. {@code findById}
 * and {@code save} are inherited from {@link JpaRepository}; the file-hash
 * lookup backs ingestion dedup, and the active/source-url lookup backs the
 * source-change monitor.
 */
public interface DocumentJpaRepository extends JpaRepository<DocumentJpaEntity, UUID> {

    Optional<DocumentJpaEntity> findByFileHash(String fileHash);

    List<DocumentJpaEntity> findByActiveTrueAndSourceUrlIsNotNull();

    Optional<DocumentJpaEntity> findByActiveTrueAndSourceUrl(String sourceUrl);
}