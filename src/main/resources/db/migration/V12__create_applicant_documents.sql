CREATE TABLE applicant_documents (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    applicant_id  UUID         NOT NULL,
    document_type VARCHAR(30)  NOT NULL
        CHECK (document_type IN ('PASSPORT')),
    storage_path  TEXT         NOT NULL,
    uploaded_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    status        VARCHAR(20)  NOT NULL
        CHECK (status IN ('UPLOADED', 'QUALITY_REJECTED', 'EXTRACTED', 'CONFIRMED'))
);

CREATE INDEX idx_applicant_documents_applicant ON applicant_documents(applicant_id);
