package com.orbitlink.server.alarm;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only views over limit alarms. */
@RestController
@RequestMapping("/api/v1/alarms")
public class AlarmController {

    private final TelemetryAlarmRepository alarmRepository;

    public AlarmController(TelemetryAlarmRepository alarmRepository) {
        this.alarmRepository = alarmRepository;
    }

    public record AlarmView(
            Long id,
            String mnemonic,
            String parameterName,
            String units,
            AlarmSeverity severity,
            String violatedLimit,
            double limitValue,
            double triggeringValue,
            double peakValue,
            int sampleCount,
            Instant raisedAt,
            Instant clearedAt,
            boolean active,
            long durationSeconds) {
    }

    /**
     * Currently active alarms, worst first.
     *
     * <p>Sorted CRITICAL before WARNING in the application rather than in SQL:
     * the severity column stores an enum name, so ordering by it in the
     * database would sort alphabetically and put CRITICAL before WARNING only
     * by luck of the alphabet. Sorting on the enum itself expresses the real
     * ordering.
     */
    @GetMapping
    @Transactional(readOnly = true)
    public List<AlarmView> active() {
        return alarmRepository.findByClearedAtIsNullOrderByRaisedAtDesc().stream()
                .map(AlarmController::toView)
                .sorted((a, b) -> {
                    int bySeverity = b.severity().compareTo(a.severity());
                    return bySeverity != 0 ? bySeverity : b.raisedAt().compareTo(a.raisedAt());
                })
                .toList();
    }

    /** Recent alarms including cleared ones, newest first. */
    @GetMapping("/history")
    @Transactional(readOnly = true)
    public List<AlarmView> history(@RequestParam(defaultValue = "50") int limit) {
        return alarmRepository.findAllByOrderByRaisedAtDesc(PageRequest.of(0, Math.min(limit, 500)))
                .stream()
                .map(AlarmController::toView)
                .toList();
    }

    public record AlarmSummary(long active, long total) {
    }

    @GetMapping("/summary")
    public AlarmSummary summary() {
        return new AlarmSummary(alarmRepository.countByClearedAtIsNull(), alarmRepository.count());
    }

    private static AlarmView toView(TelemetryAlarm alarm) {
        Instant end = alarm.getClearedAt() != null ? alarm.getClearedAt() : Instant.now();
        return new AlarmView(
                alarm.getId(),
                alarm.getParameter().getMnemonic(),
                alarm.getParameter().getName(),
                alarm.getParameter().getUnits(),
                alarm.getSeverity(),
                alarm.getViolatedLimit().name(),
                alarm.getLimitValue(),
                alarm.getTriggeringValue(),
                alarm.getPeakValue(),
                alarm.getSampleCount(),
                alarm.getRaisedAt(),
                alarm.getClearedAt(),
                alarm.isActive(),
                Duration.between(alarm.getRaisedAt(), end).toSeconds());
    }
}
