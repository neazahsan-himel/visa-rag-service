package com.neaz.travel.visa.infrastructure.persistence;

import com.neaz.travel.visa.domain.model.Application;
import com.neaz.travel.visa.domain.model.ApplicationStatus;
import com.neaz.travel.visa.domain.repository.ApplicationRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * {@link ApplicationRepository} implementation backed by PostgreSQL via
 * Spring Data JPA.
 */
@Repository
public class ApplicationRepositoryAdapter implements ApplicationRepository {

    private final ApplicationJpaRepository applicationJpaRepository;

    public ApplicationRepositoryAdapter(ApplicationJpaRepository applicationJpaRepository) {
        this.applicationJpaRepository = applicationJpaRepository;
    }

    @Override
    public Application save(Application application) {
        applicationJpaRepository.save(toEntity(application));
        return application;
    }

    @Override
    public Optional<Application> findById(UUID id) {
        return applicationJpaRepository.findById(id).map(this::toDomain);
    }

    private ApplicationJpaEntity toEntity(Application application) {
        return new ApplicationJpaEntity(
                application.getId(),
                application.getApplicantId(),
                application.getPassportIso(),
                application.getDestinationIso(),
                application.getVisaTypeCode(),
                application.getCreatedAt(),
                application.getStatus().name());
    }

    private Application toDomain(ApplicationJpaEntity entity) {
        return new Application(
                entity.getId(),
                entity.getApplicantId(),
                entity.getPassportIso(),
                entity.getDestinationIso(),
                entity.getVisaTypeCode(),
                entity.getCreatedAt(),
                ApplicationStatus.valueOf(entity.getStatus()));
    }
}
