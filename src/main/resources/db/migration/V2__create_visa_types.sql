CREATE TABLE visa_types (
    id         BIGSERIAL    PRIMARY KEY,
    country_id BIGINT       NOT NULL REFERENCES countries(id),
    code       VARCHAR(50)  NOT NULL,
    name       VARCHAR(100) NOT NULL,
    UNIQUE (country_id, code)
);