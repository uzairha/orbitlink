-- Limit checking.
--
-- Naming note: V1 called min_value/max_value "hard validity bounds" and said
-- alarm limits would arrive separately. On reflection that would have meant
-- four thresholds where two suffice, with no crisp difference between
-- "invalid" and "critical". So min_value/max_value ARE the critical (red)
-- limits, and this migration adds only the warning (yellow) pair inside them:
--
--     crit_low        warn_low      nominal      warn_high       crit_high
--   (min_value)                                                (max_value)
--   ------|--------------|-------------------------|---------------|------
--      CRITICAL      WARNING            OK              WARNING    CRITICAL
--
-- V1 is already applied and therefore immutable — an applied migration is a
-- historical record, and editing it would desynchronise every database whose
-- checksum was computed from the original. The reinterpretation is documented
-- here instead.

ALTER TABLE telemetry_parameter
    ADD COLUMN warn_low  DOUBLE PRECISION,
    ADD COLUMN warn_high DOUBLE PRECISION;

-- An alarm covers a continuous excursion, not a single sample. A parameter
-- sampled at 2 Hz that sits out of limits for a minute produces ONE alarm row
-- spanning that minute, not 120 of them.
CREATE TABLE telemetry_alarm (
    id               BIGSERIAL PRIMARY KEY,
    parameter_id     BIGINT           NOT NULL
                     REFERENCES telemetry_parameter (id),

    severity         VARCHAR(16)      NOT NULL,

    raised_at        TIMESTAMPTZ      NOT NULL DEFAULT now(),
    -- NULL means still active. Using a nullable timestamp rather than a
    -- separate boolean keeps "is it active" and "when did it end" as one fact
    -- that cannot contradict itself.
    cleared_at       TIMESTAMPTZ,

    -- The value that first broke the limit, and the limit it broke. Kept on
    -- the alarm so an operator can see what happened without joining back to
    -- the sample table or to a dictionary version that may since have changed.
    triggering_value DOUBLE PRECISION NOT NULL,
    limit_value      DOUBLE PRECISION NOT NULL,
    violated_limit   VARCHAR(16)      NOT NULL,

    -- Worst value seen and how many samples the excursion covered, updated as
    -- it continues.
    peak_value       DOUBLE PRECISION NOT NULL,
    sample_count     INTEGER          NOT NULL DEFAULT 1
);

-- At most one active alarm per parameter. The partial unique index makes that
-- a database guarantee rather than an application convention, so a race
-- between two ingestion threads cannot open two alarms for one parameter.
CREATE UNIQUE INDEX ux_alarm_one_active_per_parameter
    ON telemetry_alarm (parameter_id)
    WHERE cleared_at IS NULL;

-- Operators ask for active alarms first, then recent history.
CREATE INDEX ix_alarm_raised_at ON telemetry_alarm (raised_at DESC);
