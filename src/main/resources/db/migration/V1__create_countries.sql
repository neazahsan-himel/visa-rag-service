CREATE TABLE countries (
    id         BIGSERIAL    PRIMARY KEY,
    iso_code   VARCHAR(2)   NOT NULL UNIQUE,
    name       VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);