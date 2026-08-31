CREATE TABLE extracted_fields (
    id                   UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    applicant_profile_id UUID         NOT NULL REFERENCES applicant_profiles(id) ON DELETE CASCADE,
    source_document_id   UUID,
    field_path           VARCHAR(200) NOT NULL,
    extracted_value      TEXT         NOT NULL,
    normalized_value     TEXT         NOT NULL,
    confidence           NUMERIC(4,3) NOT NULL CHECK (confidence >= 0.0 AND confidence <= 1.0),
    verification_status  VARCHAR(20)  NOT NULL
        CHECK (verification_status IN ('UNVERIFIED', 'USER_CONFIRMED', 'USER_CORRECTED', 'REJECTED')),
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_extracted_fields_profile ON extracted_fields(applicant_profile_id);

CREATE UNIQUE INDEX idx_extracted_fields_profile_path ON extracted_fields(applicant_profile_id, field_path);
