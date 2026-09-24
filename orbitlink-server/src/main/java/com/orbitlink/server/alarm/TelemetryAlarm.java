package com.orbitlink.server.alarm;

import com.orbitlink.server.dictionary.TelemetryParameter;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * One continuous limit excursion.
 *
 * <p>Not one row per out-of-limit sample: a parameter sampled at 2 Hz that
 * stays out of limits for a minute produces one alarm spanning that minute.
 * An operator cares that the battery is too hot and for how long, not that it
 * was too hot on each of 120 consecutive readings.
 */
@Entity
@Table(name = "telemetry_alarm")
public class TelemetryAlarm {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "parameter_id", nullable = false)
    private TelemetryParameter parameter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AlarmSeverity severity;

    @Column(name = "raised_at", nullable = false)
    private Instant raisedAt;

    /** Null while the excursion is still in progress. */
    @Column(name = "cleared_at")
    private Instant clearedAt;

    @Column(name = "triggering_value", nullable = false)
    private double triggeringValue;

    @Column(name = "limit_value", nullable = false)
    private double limitValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "violated_limit", nullable = false, length = 16)
    private LimitCheck.ViolatedLimit violatedLimit;

    @Column(name = "peak_value", nullable = false)
    private double peakValue;

    @Column(name = "sample_count", nullable = false)
    private int sampleCount = 1;

    protected TelemetryAlarm() {
        // Required by JPA.
    }

    public TelemetryAlarm(TelemetryParameter parameter, LimitCheck check,
                          double value, Instant raisedAt) {
        this.parameter = parameter;
        this.severity = check.severity();
        this.violatedLimit = check.violatedLimit();
        this.limitValue = check.limitValue();
        this.triggeringValue = value;
        this.peakValue = value;
        this.raisedAt = raisedAt;
    }

    /**
     * Records another sample in the same excursion.
     *
     * <p>"Peak" means furthest from the limit, which depends on which side was
     * broken — for a low violation the worst value is the smallest.
     */
    public void recordContinuation(double value) {
        sampleCount++;
        boolean low = violatedLimit == LimitCheck.ViolatedLimit.CRITICAL_LOW
                || violatedLimit == LimitCheck.ViolatedLimit.WARNING_LOW;
        if (low ? value < peakValue : value > peakValue) {
            peakValue = value;
        }
    }

    public void clear(Instant when) {
        this.clearedAt = when;
    }

    public boolean isActive() {
        return clearedAt == null;
    }

    public Long getId() {
        return id;
    }

    public TelemetryParameter getParameter() {
        return parameter;
    }

    public AlarmSeverity getSeverity() {
        return severity;
    }

    public Instant getRaisedAt() {
        return raisedAt;
    }

    public Instant getClearedAt() {
        return clearedAt;
    }

    public double getTriggeringValue() {
        return triggeringValue;
    }

    public double getLimitValue() {
        return limitValue;
    }

    public LimitCheck.ViolatedLimit getViolatedLimit() {
        return violatedLimit;
    }

    public double getPeakValue() {
        return peakValue;
    }

    public int getSampleCount() {
        return sampleCount;
    }
}
