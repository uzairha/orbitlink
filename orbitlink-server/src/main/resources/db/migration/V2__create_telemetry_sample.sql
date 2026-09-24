-- Decoded telemetry samples.
--
-- One row per parameter per packet, so a 10-parameter packet at 2 Hz produces
-- 20 rows a second. This is the table that grows without bound, and every
-- decision below is shaped by that.

CREATE TABLE telemetry_sample (
    id            BIGSERIAL PRIMARY KEY,

    -- Foreign key rather than a denormalised mnemonic string: the mnemonic
    -- alone would be ambiguous across dictionary versions, and the parameter
    -- id pins the sample to the exact definition used to decode it. That
    -- matters when a later dictionary changes a calibration — old samples must
    -- stay interpretable under the rules that produced them.
    parameter_id  BIGINT           NOT NULL
                  REFERENCES telemetry_parameter (id),

    -- Ground receipt time. The demo spacecraft sends no CCSDS secondary header
    -- and therefore no onboard timestamp, so this is the only time available.
    -- A real mission would carry spacecraft time as well, and would care about
    -- the difference.
    received_at   TIMESTAMPTZ      NOT NULL DEFAULT now(),

    -- Carried from the packet header so a gap in the stream can be detected
    -- later without joining back through the parameter to its APID.
    apid          INTEGER          NOT NULL,
    sequence_count INTEGER         NOT NULL,

    -- Both forms are kept. The raw counts are what actually arrived and are
    -- the only thing that can be re-interpreted if a calibration turns out to
    -- be wrong; the engineering value is what every query wants and is
    -- expensive to recompute across millions of rows.
    raw_value     BIGINT           NOT NULL,
    eng_value     DOUBLE PRECISION NOT NULL
);

-- The dominant query is "this parameter, over this time window", for plotting
-- and for limit checking. A composite index in that order serves it directly;
-- DESC because recent data is what gets asked for.
CREATE INDEX ix_sample_parameter_time
    ON telemetry_sample (parameter_id, received_at DESC);

-- Secondary access path for packet-level questions, e.g. finding sequence
-- count gaps within one APID.
CREATE INDEX ix_sample_apid_time
    ON telemetry_sample (apid, received_at DESC);
