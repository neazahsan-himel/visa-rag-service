package com.neaz.travel.visa.domain.model;

import com.neaz.travel.visa.common.exception.VisaRagException;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * An applicant's visa application: the passport/destination/visa-type scope
 * ({@link #passportIso}/{@link #destinationIso}/{@link #visaTypeCode}, the
 * same shape as {@link MetadataFilter}) that {@code ChecklistService} and
 * {@code ConsistencyValidationService} operate against. Identity is defined
 * by {@code id}. Does not itself track submission to the destination's
 * immigration authority — this system never submits on the applicant's
 * behalf (see {@code docs/VISA_APPLICATION_ASSISTANT_PROPOSAL.md} §5).
 *
 * <p>Lifecycle: {@code OPEN} from creation until the applicant {@link
 * #withdraw}s it. There is no "completed" state yet — that belongs to a
 * later phase once form preparation (Phase E) exists.
 */
public final class Application {

    private final UUID id;
    private final UUID applicantId;
    private final String passportIso;
    private final String destinationIso;
    private final String visaTypeCode;
    private final Instant createdAt;
    private ApplicationStatus status;

    public Application(UUID id, UUID applicantId, String passportIso, String destinationIso, String visaTypeCode,
                        Instant createdAt, ApplicationStatus status) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.applicantId = Objects.requireNonNull(applicantId, "applicantId must not be null");
        this.passportIso = requireNonBlank(passportIso, "passportIso");
        this.destinationIso = requireNonBlank(destinationIso, "destinationIso");
        this.visaTypeCode = requireNonBlank(visaTypeCode, "visaTypeCode");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }

    /**
     * Abandons this application before any submission-related work.
     *
     * @throws VisaRagException if this application is not currently {@code OPEN}
     */
    public void withdraw() {
        if (status != ApplicationStatus.OPEN) {
            throw new VisaRagException(
                    "Cannot withdraw application '" + id + "': expected status OPEN but was " + status);
        }
        this.status = ApplicationStatus.WITHDRAWN;
    }

    public UUID getId() {
        return id;
    }

    public UUID getApplicantId() {
        return applicantId;
    }

    public String getPassportIso() {
        return passportIso;
    }

    public String getDestinationIso() {
        return destinationIso;
    }

    public String getVisaTypeCode() {
        return visaTypeCode;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public ApplicationStatus getStatus() {
        return status;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Application other)) {
            return false;
        }
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Application{id=%s, applicantId=%s, passportIso=%s, destinationIso=%s, visaTypeCode=%s, status=%s}"
                .formatted(id, applicantId, passportIso, destinationIso, visaTypeCode, status);
    }
}
