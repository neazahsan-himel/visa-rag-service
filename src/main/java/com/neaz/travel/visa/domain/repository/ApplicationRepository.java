package com.neaz.travel.visa.domain.repository;

import com.neaz.travel.visa.domain.model.Application;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository contract for {@link Application} entities, implemented by
 * {@code infrastructure/persistence} against PostgreSQL.
 */
public interface ApplicationRepository {

    Application save(Application application);

    Optional<Application> findById(UUID id);
}
