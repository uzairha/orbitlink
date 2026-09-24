-- Telemetry dictionary schema.
--
-- A "dictionary" is one versioned description of everything the spacecraft can
-- downlink: which packets exist, which parameters live inside them, where each
-- parameter's bits sit, and how to turn those bits into engineering units.
--
-- Versioning is first-class because a dictionary changes over a mission's life
-- while old telemetry must still be decodable. Rows are therefore never edited
-- in place: a new dictionary version is inserted alongside the old ones, and
-- exactly one is marked active.

CREATE TABLE telemetry_dictionary (
    id          BIGSERIAL PRIMARY KEY,
    version     VARCHAR(50)  NOT NULL,
    description TEXT,
    source_file VARCHAR(255),
    active      BOOLEAN      NOT NULL DEFAULT FALSE,
    loaded_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_dictionary_version UNIQUE (version)
);

-- At most one dictionary may be active at a time. A partial unique index is
-- the right tool: it constrains only the rows where active is true, so any
-- number of inactive dictionaries can coexist. Enforcing this in application
-- code instead would leave a race between two concurrent activations.
CREATE UNIQUE INDEX ux_dictionary_single_active
    ON telemetry_dictionary (active)
    WHERE active;

CREATE TABLE telemetry_parameter (
    id            BIGSERIAL PRIMARY KEY,
    dictionary_id BIGINT       NOT NULL
                  REFERENCES telemetry_dictionary (id) ON DELETE CASCADE,

    -- Short operator-facing identifier, e.g. BATT_BUS_V. Unique per
    -- dictionary, not globally: the same mnemonic legitimately appears in
    -- successive dictionary versions.
    mnemonic      VARCHAR(64)  NOT NULL,
    name          VARCHAR(128) NOT NULL,
    description   TEXT,

    -- CCSDS Application Process Identifier: which packet carries this
    -- parameter. Bit offsets are only meaningful relative to a packet, so
    -- decoding (phase 5) and overlap checking (phase 3) both need this.
    apid          INTEGER      NOT NULL,

    data_type     VARCHAR(32)  NOT NULL,
    -- Offset from the first bit of the packet data field, not of the packet,
    -- so the 6-byte CCSDS primary header is excluded.
    bit_offset    INTEGER      NOT NULL,
    bit_length    INTEGER      NOT NULL,
    units         VARCHAR(32),

    -- Hard validity bounds in engineering units. Nullable: a status flag or an
    -- enum has no meaningful numeric range. Warning/critical alarm limits are
    -- a separate concern and arrive in phase 6.
    min_value     DOUBLE PRECISION,
    max_value     DOUBLE PRECISION,

    -- Linear calibration: engineering = raw * cal_scale + cal_offset.
    -- Defaulted rather than nullable so the decoder never has to null-check on
    -- the hot path; an uncalibrated parameter is simply scale 1, offset 0.
    cal_scale     DOUBLE PRECISION NOT NULL DEFAULT 1.0,
    cal_offset    DOUBLE PRECISION NOT NULL DEFAULT 0.0,

    CONSTRAINT uq_parameter_mnemonic_per_dictionary UNIQUE (dictionary_id, mnemonic),
    CONSTRAINT ck_parameter_bit_offset_non_negative CHECK (bit_offset >= 0),
    CONSTRAINT ck_parameter_bit_length_positive     CHECK (bit_length > 0)
);

-- The decoder looks parameters up by (dictionary, apid) for every packet it
-- receives, so that lookup gets an index rather than a sequential scan.
CREATE INDEX ix_parameter_dictionary_apid
    ON telemetry_parameter (dictionary_id, apid);

CREATE TABLE telemetry_parameter_enum (
    id           BIGSERIAL PRIMARY KEY,
    parameter_id BIGINT      NOT NULL
                 REFERENCES telemetry_parameter (id) ON DELETE CASCADE,

    -- The raw coded value as it appears on the wire, and the label operators
    -- should see for it. BIGINT because a raw field may be up to 32 bits
    -- unsigned, which overflows a signed INTEGER.
    raw_value    BIGINT      NOT NULL,
    label        VARCHAR(64) NOT NULL,

    CONSTRAINT uq_enum_raw_value_per_parameter UNIQUE (parameter_id, raw_value)
);
