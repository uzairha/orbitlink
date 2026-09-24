package com.orbitlink.server.dictionary;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * One coded value of an ENUM parameter and the label operators see for it,
 * e.g. raw 2 -> "SAFE_MODE".
 */
@Entity
@Table(name = "telemetry_parameter_enum")
public class ParameterEnumState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "parameter_id", nullable = false)
    private TelemetryParameter parameter;

    /** long, not int: a 32-bit unsigned raw field does not fit a signed int. */
    @Column(name = "raw_value", nullable = false)
    private long rawValue;

    @Column(nullable = false, length = 64)
    private String label;

    protected ParameterEnumState() {
        // Required by JPA.
    }

    public ParameterEnumState(long rawValue, String label) {
        this.rawValue = rawValue;
        this.label = label;
    }

    public Long getId() {
        return id;
    }

    public TelemetryParameter getParameter() {
        return parameter;
    }

    void setParameter(TelemetryParameter parameter) {
        this.parameter = parameter;
    }

    public long getRawValue() {
        return rawValue;
    }

    public String getLabel() {
        return label;
    }
}
