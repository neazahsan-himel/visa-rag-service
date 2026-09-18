package com.neaz.travel.visa.application.result;

import com.neaz.travel.visa.domain.model.ExtractedField;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Outcome of the "does this applicant's profile have unresolved cross-source
 * conflicts" use case, returned by {@code ConsistencyValidationService} to
 * {@code ApplicationController}. Today the only conflicts this can surface
 * are MRZ-vs-visual disagreements left by {@code DocumentIntelligenceService}
 * ({@code ApplicantProfile.CONFLICT_SUFFIX_MRZ}/{@code CONFLICT_SUFFIX_VISUAL}) —
 * true cross-document checks (e.g. passport vs. a future NID document type)
 * are not possible yet since {@code DocumentType} has only {@code PASSPORT}.
 * Conflicts are surfaced, never silently resolved, matching the RAG
 * pipeline's existing source-conflict posture.
 *
 * @param applicantId the applicant this check was run for
 * @param consistent  {@code true} iff {@code conflicts} is empty
 * @param conflicts   every unresolved conflicting field, most-recently-set order not guaranteed
 */
public record ConsistencyCheckResult(UUID applicantId, boolean consistent, List<FieldConflict> conflicts) {

    public ConsistencyCheckResult {
        Objects.requireNonNull(applicantId, "applicantId must not be null");
        conflicts = List.copyOf(Objects.requireNonNull(conflicts, "conflicts must not be null"));
        if (consistent != conflicts.isEmpty()) {
            throw new IllegalArgumentException("consistent must be true iff conflicts is empty");
        }
    }

    /**
     * One field where two extraction sources disagree.
     *
     * @param fieldPath   the plain {@code ApplicantProfile.PATH_*} the two readings disagree on
     * @param mrzReading   the MRZ-sourced reading
     * @param visualReading the vision-sourced reading
     */
    public record FieldConflict(String fieldPath, ExtractedField<?> mrzReading, ExtractedField<?> visualReading) {

        public FieldConflict {
            if (fieldPath == null || fieldPath.isBlank()) {
                throw new IllegalArgumentException("fieldPath must not be blank");
            }
            Objects.requireNonNull(mrzReading, "mrzReading must not be null");
            Objects.requireNonNull(visualReading, "visualReading must not be null");
        }
    }
}
