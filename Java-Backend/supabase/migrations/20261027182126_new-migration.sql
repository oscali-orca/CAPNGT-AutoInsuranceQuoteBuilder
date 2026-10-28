-- T-07 · Core schema for the training quote exercise
-- Tables: customer, rate_configuration, saved_quote
-- Money is always NUMERIC (exact decimal). Never use REAL / DOUBLE PRECISION for money.

-- ---------------------------------------------------------------------------
-- customer: fictional seeded customers (no real sign-up flow in this exercise)
-- ("user" is a reserved word in PostgreSQL, so the table is called customer)
-- ---------------------------------------------------------------------------
CREATE TABLE customer
(
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL UNIQUE,
    display_name  VARCHAR(100) NOT NULL,
    password_hash VARCHAR(100) NOT NULL, -- BCrypt hash, never plain text
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT customer_username_not_blank CHECK (btrim(username) <> '')
);

-- ---------------------------------------------------------------------------
-- rate_configuration: one row per pricing version (e.g. TRAINING-1)
-- Rows are inserted by T-08. Exactly one version may be active at a time.
-- config holds the version's rates; JSONB stores numbers as exact numerics,
-- so Java must read them with BigDecimal (never double).
-- ---------------------------------------------------------------------------
CREATE TABLE rate_configuration
(
    id           BIGSERIAL PRIMARY KEY,
    version_code VARCHAR(30) NOT NULL UNIQUE, -- e.g. 'TRAINING-1'
    description  VARCHAR(255),
    currency     CHAR(3)     NOT NULL DEFAULT 'USD',
    is_active    BOOLEAN     NOT NULL DEFAULT FALSE,
    config       JSONB       NOT NULL,        -- shape owned by T-08
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT rate_config_is_object CHECK (jsonb_typeof(config) = 'object')
);

-- At most one active rate version
CREATE UNIQUE INDEX rate_configuration_one_active
    ON rate_configuration (is_active) WHERE is_active;

-- ---------------------------------------------------------------------------
-- saved_quote: immutable, customer-owned snapshot (US-04)
-- Searchable/validated fields are columns; full breakdowns live in snapshot.
-- ---------------------------------------------------------------------------
CREATE TABLE saved_quote
(
    id                    BIGSERIAL PRIMARY KEY,
    quote_reference       VARCHAR(20)    NOT NULL UNIQUE,     -- shown to the customer, e.g. 'Q-7F3K9A'
    customer_id           BIGINT         NOT NULL REFERENCES customer (id),
    request_key           UUID           NOT NULL,            -- idempotency key sent by React per save attempt
    quote_name            VARCHAR(100)   NOT NULL,
    rate_configuration_id BIGINT         NOT NULL REFERENCES rate_configuration (id),
    rate_version_code     VARCHAR(30)    NOT NULL,            -- copied so the snapshot is self-describing

    -- Risk inputs (US-01 ranges, all integers)
    driver_age            INTEGER        NOT NULL CHECK (driver_age BETWEEN 18 AND 80),
    vehicle_age           INTEGER        NOT NULL CHECK (vehicle_age BETWEEN 0 AND 20),
    annual_mileage        INTEGER        NOT NULL CHECK (annual_mileage BETWEEN 0 AND 50000),
    prior_claims          INTEGER        NOT NULL CHECK (prior_claims BETWEEN 0 AND 3),

    -- Coverage choice (US-02 / US-03)
    comparison_deductible INTEGER        NOT NULL CHECK (comparison_deductible IN (500, 1000, 2000)),
    selected_package      VARCHAR(10)    NOT NULL CHECK (selected_package IN ('BASIC', 'STANDARD', 'ENHANCED')),

    -- Annual totals for all three packages (for the saved list without parsing JSON)
    basic_total           NUMERIC(12, 2) NOT NULL CHECK (basic_total >= 0),
    standard_total        NUMERIC(12, 2) NOT NULL CHECK (standard_total >= 0),
    enhanced_total        NUMERIC(12, 2) NOT NULL CHECK (enhanced_total >= 0),

    snapshot              JSONB          NOT NULL,            -- full breakdowns, see DATA_DICTIONARY.md
    duplicated_from_id    BIGINT REFERENCES saved_quote (id), -- set when saved from "Duplicate & edit"
    created_at            TIMESTAMPTZ    NOT NULL DEFAULT now(),

    CONSTRAINT saved_quote_name_not_blank CHECK (btrim(quote_name) <> ''),
    CONSTRAINT saved_quote_snapshot_is_object CHECK (jsonb_typeof(snapshot) = 'object'),
    -- Retrying the same save request must not create a second record
    CONSTRAINT saved_quote_customer_request_unique UNIQUE (customer_id, request_key)
);

-- Saved list: "my quotes, newest first"
CREATE INDEX saved_quote_customer_created_idx
    ON saved_quote (customer_id, created_at DESC);

-- ---------------------------------------------------------------------------
-- Immutability: saved quotes are insert-only. Duplicating creates a NEW row.
-- ---------------------------------------------------------------------------
CREATE FUNCTION saved_quote_block_changes() RETURNS trigger AS $$
BEGIN
    RAISE
EXCEPTION 'saved_quote rows are immutable; create a new quote instead'
        USING ERRCODE = 'restrict_violation';
END;
$$
LANGUAGE plpgsql;

CREATE TRIGGER saved_quote_immutable
    BEFORE UPDATE OR
DELETE
ON saved_quote
    FOR EACH ROW EXECUTE FUNCTION saved_quote_block_changes();
