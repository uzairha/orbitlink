package com.orbitlink.server.dictionary;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;

/** One decodable field within a telemetry packet. */
@Entity
@Table(name = "telemetry_parameter")
public class TelemetryParameter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * LAZY, not the ManyToOne default of EAGER. Loading a parameter should not
     * drag its whole dictionary — and every sibling parameter — into memory,
     * which is what EAGER would do on every single lookup.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dictionary_id", nullable = false)
    private TelemetryDictionary dictionary;

    @Column(nullable = false, length = 64)
    private String mnemonic;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column(nullable = false)
    private int apid;

    /** STRING, not ORDINAL — see {@link ParameterDataType}. */
    @Enumerated(EnumType.STRING)
    @Column(name = "data_type", nullable = false, length = 32)
    private ParameterDataType dataType;

    @Column(name = "bit_offset", nullable = false)
    private int bitOffset;

    @Column(name = "bit_length", nullable = false)
    private int bitLength;

    @Column(length = 32)
    private String units;

    /**
     * Critical (red) limits. Boxed Double, not double: null means "no limit
     * defined", which 0.0 cannot express.
     *
     * <p>These columns are named min/max for historical reasons — see the V3
     * migration. They are the CRITICAL thresholds; {@link #warnLow} and
     * {@link #warnHigh} sit inside them.
     */
    @Column(name = "min_value")
    private Double minValue;

    @Column(name = "max_value")
    private Double maxValue;

    /** Warning (yellow) limits, nested inside the critical pair. */
    @Column(name = "warn_low")
    private Double warnLow;

    @Column(name = "warn_high")
    private Double warnHigh;

    @Column(name = "cal_scale", nullable = false)
    private double calScale = 1.0;

    @Column(name = "cal_offset", nullable = false)
    private double calOffset = 0.0;

    @OneToMany(mappedBy = "parameter", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ParameterEnumState> enumStates = new ArrayList<>();

    protected TelemetryParameter() {
        // Required by JPA.
    }

    public TelemetryParameter(String mnemonic, String name, int apid,
                              ParameterDataType dataType, int bitOffset, int bitLength) {
        this.mnemonic = mnemonic;
        this.name = name;
        this.apid = apid;
        this.dataType = dataType;
        this.bitOffset = bitOffset;
        this.bitLength = bitLength;
    }

    /** Applies the linear calibration to a raw counts value. */
    public double calibrate(double rawValue) {
        return rawValue * calScale + calOffset;
    }

    /** Bit position one past the last bit of this parameter. */
    public int bitEndExclusive() {
        return bitOffset + bitLength;
    }

    public void addEnumState(ParameterEnumState state) {
        enumStates.add(state);
        state.setParameter(this);
    }

    public Long getId() {
        return id;
    }

    public TelemetryDictionary getDictionary() {
        return dictionary;
    }

    void setDictionary(TelemetryDictionary dictionary) {
        this.dictionary = dictionary;
    }

    public String getMnemonic() {
        return mnemonic;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getApid() {
        return apid;
    }

    public ParameterDataType getDataType() {
        return dataType;
    }

    public int getBitOffset() {
        return bitOffset;
    }

    public int getBitLength() {
        return bitLength;
    }

    public String getUnits() {
        return units;
    }

    public void setUnits(String units) {
        this.units = units;
    }

    public Double getMinValue() {
        return minValue;
    }

    public void setMinValue(Double minValue) {
        this.minValue = minValue;
    }

    public Double getMaxValue() {
        return maxValue;
    }

    public void setMaxValue(Double maxValue) {
        this.maxValue = maxValue;
    }

    public Double getWarnLow() {
        return warnLow;
    }

    public void setWarnLow(Double warnLow) {
        this.warnLow = warnLow;
    }

    public Double getWarnHigh() {
        return warnHigh;
    }

    public void setWarnHigh(Double warnHigh) {
        this.warnHigh = warnHigh;
    }

    public double getCalScale() {
        return calScale;
    }

    public void setCalScale(double calScale) {
        this.calScale = calScale;
    }

    public double getCalOffset() {
        return calOffset;
    }

    public void setCalOffset(double calOffset) {
        this.calOffset = calOffset;
    }

    public List<ParameterEnumState> getEnumStates() {
        return enumStates;
    }
}
