package com.neaz.travel.visa.application.applicant;

import com.neaz.travel.visa.application.result.ConsistencyCheckResult;
import com.neaz.travel.visa.common.exception.ApplicantProfileNotFoundException;
import com.neaz.travel.visa.common.exception.ApplicationNotFoundException;
import com.neaz.travel.visa.domain.model.ApplicantProfile;
import com.neaz.travel.visa.domain.model.ExtractedField;
import com.neaz.travel.visa.domain.repository.ApplicantProfileRepository;
import com.neaz.travel.visa.domain.repository.ApplicationRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Scans an applicant's profile for unresolved cross-source conflicts — see
 * {@link ConsistencyCheckResult}'s Javadoc for what "conflict" means today.
 * Contains no persistence logic itself — that lives behind {@link
 * ApplicantProfileRepository}.
 */
@Service
public class ConsistencyValidationService {

    private final ApplicationRepository applicationRepository;
    private final ApplicantProfileRepository applicantProfileRepository;

    public ConsistencyValidationService(ApplicationRepository applicationRepository,
                                         ApplicantProfileRepository applicantProfileRepository) {
        this.applicationRepository = applicationRepository;
        this.applicantProfileRepository = applicantProfileRepository;
    }

    /**
     * @throws ApplicationNotFoundException       if no application with {@code applicationId} is owned by {@code applicantId}
     * @throws ApplicantProfileNotFoundException  if the applicant has no profile yet
     */
    public ConsistencyCheckResult checkConsistency(UUID applicantId, UUID applicationId) {
        applicationRepository.findById(applicationId)
                .filter(application -> application.getApplicantId().equals(applicantId))
                .orElseThrow(() -> new ApplicationNotFoundException(applicationId));

        ApplicantProfile profile = applicantProfileRepository.findByApplicantId(applicantId)
                .orElseThrow(() -> new ApplicantProfileNotFoundException(applicantId));

        List<ConsistencyCheckResult.FieldConflict> conflicts = findConflicts(profile.getFields());
        return new ConsistencyCheckResult(applicantId, conflicts.isEmpty(), conflicts);
    }

    private static List<ConsistencyCheckResult.FieldConflict> findConflicts(Map<String, ExtractedField<?>> fields) {
        List<ConsistencyCheckResult.FieldConflict> conflicts = new ArrayList<>();
        for (String key : fields.keySet()) {
            if (!key.endsWith(ApplicantProfile.CONFLICT_SUFFIX_MRZ)) {
                continue;
            }
            String fieldPath = key.substring(0, key.length() - ApplicantProfile.CONFLICT_SUFFIX_MRZ.length());
            ExtractedField<?> mrzReading = fields.get(key);
            ExtractedField<?> visualReading = fields.get(fieldPath + ApplicantProfile.CONFLICT_SUFFIX_VISUAL);
            if (visualReading != null) {
                conflicts.add(new ConsistencyCheckResult.FieldConflict(fieldPath, mrzReading, visualReading));
            }
        }
        return conflicts;
    }
}
