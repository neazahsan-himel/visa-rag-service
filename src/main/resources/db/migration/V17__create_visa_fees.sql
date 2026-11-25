-- Structured visa fees, served by the /fees endpoint (not by the RAG pipeline).
--
-- A fee is only stored with its official provenance: source_url, the verbatim
-- source_quote it was taken from, and the date a human verified it. The 90-day
-- staleness rule is applied in the domain (FeeFreshnessPolicy), not here.
--
-- Re-verifying a fee inserts a new row and deactivates the old one, mirroring
-- document supersession, so earlier values remain for audit.
CREATE TABLE visa_fees (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    country_id    BIGINT       NOT NULL REFERENCES countries(id),
    visa_type_id  BIGINT       NOT NULL REFERENCES visa_types(id),
    passport_iso  VARCHAR(2)   NOT NULL,
    source_url    TEXT         NOT NULL,
    source_quote  TEXT         NOT NULL CHECK (length(trim(source_quote)) > 0),
    verified_at   DATE         NOT NULL,
    active        BOOLEAN      NOT NULL DEFAULT true,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- At most one active fee per passport/destination/visa type. A GENERAL
-- visa-type row applies to every visa type that has no fee of its own.
CREATE UNIQUE INDEX idx_visa_fees_active
    ON visa_fees(country_id, passport_iso, visa_type_id) WHERE active;

-- Component types follow the official India fee policy wording: basic fee,
-- special fee, and processing fee charged by the out-sourcing agency.
-- Currency is per component because components may be charged in different
-- currencies; no conversion is stored.
CREATE TABLE visa_fee_components (
    id              UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    visa_fee_id     UUID           NOT NULL REFERENCES visa_fees(id) ON DELETE CASCADE,
    component_type  VARCHAR(20)    NOT NULL
        CHECK (component_type IN ('BASIC', 'SPECIAL', 'PROCESSING')),
    charged_by      VARCHAR(200)   NOT NULL,
    amount          NUMERIC(12, 2) NOT NULL CHECK (amount >= 0),
    currency        VARCHAR(3)     NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    -- Also serves lookups by visa_fee_id (leading column), so no separate index.
    UNIQUE (visa_fee_id, component_type)
);
