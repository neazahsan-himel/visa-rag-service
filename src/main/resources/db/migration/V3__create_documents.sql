CREATE TABLE documents (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    country_id   BIGINT       NOT NULL REFERENCES countries(id),
    visa_type_id BIGINT       NOT NULL REFERENCES visa_types(id),
    passport_iso VARCHAR(2)   NOT NULL,
    title        VARCHAR(500) NOT NULL,
    source_url   TEXT,
    file_hash    VARCHAR(64)  NOT NULL,
    last_updated DATE         NOT NULL,
    version      VARCHAR(20),
    ingested_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    active       BOOLEAN      NOT NULL DEFAULT true
);