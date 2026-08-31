CREATE TABLE applicant_profiles (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    applicant_id     UUID         NOT NULL,
    purpose_of_visit TEXT,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_modified_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_applicant_profiles_applicant ON applicant_profiles(applicant_id);
