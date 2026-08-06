package com.neaz.travel.visa.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data repository for {@link DocumentJpaEntity}. {@code findById}
 * and {@code save} are inherited from {@link JpaRepository}; the only
 * addition is the file-hash lookup used for ingestion dedup.
 */
public interface DocumentJpaRepository extends JpaRepository<DocumentJpaEntity, UUID> {

    Optional<DocumentJpaEntity> findByFileHash(String fileHash);
}