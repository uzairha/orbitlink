package com.orbitlink.server.alarm;

import com.orbitlink.server.dictionary.TelemetryParameter;
import com.orbitlink.server.telemetry.DecodedValue;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns a stream of decoded values into alarms.
 *
 * <p>The whole design question here is <em>transitions, not samples</em>. A
 * parameter sitting out of limits at 2 Hz produces a violating sample every
 * 500 ms; writing a row for each would bury the operator and hammer the
 * database. So this tracks the last severity per parameter in memory and
 * touches the database only when that severity changes:
 *
 * <pre>
 *   OK       -> WARNING/CRITICAL   raise a new alarm
 *   WARNING  -> CRITICAL           clear and re-raise (escalation is a new event)
 *   CRITICAL -> WARNING            clear and re-raise (de-escalation, still notable)
 *   violation-> OK                 clear the active alarm
 *   unchanged                      update peak value and sample count only
 * </pre>
 *
 * <p>The in-memory map is a cache of database state, not the source of truth.
 * It is rebuilt from the active alarms on startup, so a restart mid-excursion
 * does not orphan an alarm or raise a duplicate.
 */
@Service
public class AlarmService {

    private static final Logger log = LoggerFactory.getLogger(AlarmService.class);

    private final TelemetryAlarmRepository alarmRepository;

    /** parameterId -> last severity seen. Read and written on the ingestion path. */
    private final Map<Long, AlarmSeverity> lastSeverity = new ConcurrentHashMap<>();

    public AlarmService(TelemetryAlarmRepository alarmRepository) {
        this.alarmRepository = alarmRepository;
    }

    /**
     * Rebuilds the in-memory severity map from alarms still open in the
     * database. Without this, a restart during an excursion would see the
     * parameter as OK, and the next violating sample would try to raise a
     * second active alarm — which the partial unique index would reject.
     */
    @Transactional(readOnly = true)
    public void restoreFromDatabase() {
        lastSeverity.clear();
        List<TelemetryAlarm> active = alarmRepository.findByClearedAtIsNullOrderByRaisedAtDesc();
        active.forEach(alarm ->
                lastSeverity.put(alarm.getParameter().getId(), alarm.getSeverity()));
        log.info("Restored {} active alarm(s)", active.size());
    }

    /** Evaluates every value from one packet. Runs inside the ingestion transaction. */
    @Transactional
    public void evaluate(List<DecodedValue> values, Instant observedAt) {
        for (DecodedValue value : values) {
            evaluateOne(value.parameter(), value.engValue(), observedAt);
        }
    }

    private void evaluateOne(TelemetryParameter parameter, double value, Instant observedAt) {
        LimitCheck check = LimitChecker.check(parameter, value);
        Long parameterId = parameter.getId();

        AlarmSeverity previous = lastSeverity.getOrDefault(parameterId, AlarmSeverity.OK);
        AlarmSeverity current = check.severity();

        if (previous == current) {
            if (current.isViolation()) {
                // Same excursion continuing: fold into the existing row rather
                // than writing a new one.
                alarmRepository.findByParameterIdAndClearedAtIsNull(parameterId)
                        .ifPresent(alarm -> alarm.recordContinuation(value));
            }
            return;
        }

        // Any change of state closes whatever was open.
        Optional<TelemetryAlarm> open = alarmRepository.findByParameterIdAndClearedAtIsNull(parameterId);
        open.ifPresent(alarm -> {
            alarm.clear(observedAt);
            log.info("Alarm cleared: {} {} after {} samples (peak {})",
                    parameter.getMnemonic(), alarm.getSeverity(),
                    alarm.getSampleCount(), alarm.getPeakValue());
        });

        if (current.isViolation()) {
            // flush() so the clear above is written before the insert below.
            // Both rows touch the partial unique index on active alarms, and
            // without ordering the insert can be rejected by an alarm the same
            // transaction is in the middle of closing.
            alarmRepository.flush();

            TelemetryAlarm raised = new TelemetryAlarm(parameter, check, value, observedAt);
            alarmRepository.save(raised);
            log.warn("Alarm raised: {} {} value={} {} limit={}",
                    parameter.getMnemonic(), current, value,
                    check.violatedLimit(), check.limitValue());
        }

        lastSeverity.put(parameterId, current);
    }

    /** Clears cached state, for tests and for a dictionary reload. */
    public void resetCache() {
        lastSeverity.clear();
    }
}
