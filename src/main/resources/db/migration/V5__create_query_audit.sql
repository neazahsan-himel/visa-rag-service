CREATE TABLE query_audit (
    id                  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    query_text          TEXT         NOT NULL,
    passport_iso        CHAR(2)      NOT NULL,
    destination_iso     CHAR(2)      NOT NULL,
    visa_type_code      VARCHAR(50),
    retrieved_chunk_ids UUID[]       NOT NULL,
    confidence_score    NUMERIC(4,3) NOT NULL,
    answer_text         TEXT         NOT NULL,
    model_id            VARCHAR(100) NOT NULL,
    latency_ms          INT          NOT NULL,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_created ON query_audit(created_at DESC);