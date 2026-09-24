package com.orbitlink.server.telemetry;

import com.orbitlink.server.dictionary.TelemetryParameter;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/** One decoded parameter value from one received packet. */
@Entity
@Table(name = "telemetry_sample")
public class TelemetrySample {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "parameter_id", nullable = false)
    private TelemetryParameter parameter;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(nullable = false)
    private int apid;

    @Column(name = "sequence_count", nullable = false)
    private int sequenceCount;

    @Column(name = "raw_value", nullable = false)
    private long rawValue;

    @Column(name = "eng_value", nullable = false)
    private double engValue;

    protected TelemetrySample() {
        // Required by JPA.
    }

    public TelemetrySample(TelemetryParameter parameter, Instant receivedAt,
                           int apid, int sequenceCount, long rawValue, double engValue) {
        this.parameter = parameter;
        this.receivedAt = receivedAt;
        this.apid = apid;
        this.sequenceCount = sequenceCount;
        this.rawValue = rawValue;
        this.engValue = engValue;
    }

    public Long getId() {
        return id;
    }

    public TelemetryParameter getParameter() {
        return parameter;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public int getApid() {
        return apid;
    }

    public int getSequenceCount() {
        return sequenceCount;
    }

    public long getRawValue() {
        return rawValue;
    }

    public double getEngValue() {
        return engValue;
    }
}
