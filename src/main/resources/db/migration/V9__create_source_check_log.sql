CREATE TABLE source_check_log (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id    UUID         NOT NULL REFERENCES documents(id),
    checked_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    detected_hash  VARCHAR(64),
    status         VARCHAR(20)  NOT NULL CHECK (status IN ('UNCHANGED', 'CHANGED', 'FETCH_FAILED')),
    reviewed       BOOLEAN      NOT NULL DEFAULT false
);

CREATE INDEX idx_source_check_log_document ON source_check_log(document_id, checked_at DESC);

CREATE INDEX idx_source_check_log_pending ON source_check_log(status, reviewed)
    WHERE status = 'CHANGED' AND reviewed = false;
