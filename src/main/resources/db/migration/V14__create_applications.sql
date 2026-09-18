CREATE TABLE applications (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    applicant_id     UUID         NOT NULL,
    passport_iso     VARCHAR(2)   NOT NULL,
    destination_iso  VARCHAR(2)   NOT NULL,
    visa_type_code   VARCHAR(30)  NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    status           VARCHAR(20)  NOT NULL
        CHECK (status IN ('OPEN', 'WITHDRAWN'))
);

CREATE INDEX idx_applications_applicant ON applications(applicant_id);
