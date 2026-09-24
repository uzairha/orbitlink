-- Command definitions and the log of every command issued.
--
-- Commands are the uplink counterpart to telemetry: the dictionary describes
-- what may be sent, and every attempt is recorded whether or not it was valid.

CREATE TABLE command_definition (
    id            BIGSERIAL PRIMARY KEY,
    dictionary_id BIGINT       NOT NULL
                  REFERENCES telemetry_dictionary (id) ON DELETE CASCADE,

    mnemonic      VARCHAR(64)  NOT NULL,
    name          VARCHAR(128) NOT NULL,
    description   TEXT,

    -- CCSDS apid for the uplink packet this command becomes.
    apid          INTEGER      NOT NULL,
    -- Function code distinguishing commands that share an apid.
    function_code INTEGER      NOT NULL,

    -- A command that changes vehicle state in a way that is hard to undo
    -- (safe mode, thruster firing) should require explicit confirmation.
    hazardous     BOOLEAN      NOT NULL DEFAULT FALSE,

    CONSTRAINT uq_command_mnemonic_per_dictionary UNIQUE (dictionary_id, mnemonic),
    CONSTRAINT uq_command_code_per_dictionary UNIQUE (dictionary_id, apid, function_code)
);

CREATE TABLE command_argument (
    id            BIGSERIAL PRIMARY KEY,
    command_id    BIGINT       NOT NULL
                  REFERENCES command_definition (id) ON DELETE CASCADE,

    name          VARCHAR(64)  NOT NULL,
    description   TEXT,
    data_type     VARCHAR(32)  NOT NULL,

    -- Position in the argument list. Arguments are ordered because they are
    -- packed into the uplink packet in sequence.
    position      INTEGER      NOT NULL,

    -- Range constraints for numeric arguments; null means unconstrained.
    min_value     DOUBLE PRECISION,
    max_value     DOUBLE PRECISION,

    -- Allowed values for an ENUM argument, stored as a comma-separated list.
    -- A child table would be more normalised, but these are short fixed lists
    -- read only during validation, never queried or joined on.
    allowed_values TEXT,

    required      BOOLEAN      NOT NULL DEFAULT TRUE,

    CONSTRAINT uq_argument_name_per_command UNIQUE (command_id, name),
    CONSTRAINT uq_argument_position_per_command UNIQUE (command_id, position)
);

-- Every command attempt, accepted or rejected.
--
-- Rejected attempts are logged too, and that is the point: in a flight
-- operations context "who tried to send what, when, and why was it refused"
-- is exactly what an anomaly review needs. Discarding failures would throw
-- away the most interesting rows.
CREATE TABLE command_log (
    id            BIGSERIAL PRIMARY KEY,
    command_id    BIGINT       REFERENCES command_definition (id),

    -- Kept as text as well as by id: the mnemonic is what the operator typed,
    -- and an unknown mnemonic has no id to reference at all.
    mnemonic      VARCHAR(64)  NOT NULL,

    issued_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    issued_by     VARCHAR(64)  NOT NULL,

    -- The arguments as submitted, verbatim, so the log reflects the request
    -- rather than a normalised interpretation of it.
    arguments     JSONB        NOT NULL DEFAULT '{}'::jsonb,

    status        VARCHAR(16)  NOT NULL,
    rejection_reason TEXT
);

CREATE INDEX ix_command_log_issued_at ON command_log (issued_at DESC);
CREATE INDEX ix_command_log_status ON command_log (status, issued_at DESC);
