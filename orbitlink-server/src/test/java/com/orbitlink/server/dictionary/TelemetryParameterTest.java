package com.orbitlink.server.dictionary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

/** Calibration and bit-range arithmetic, both pure functions on the entity. */
class TelemetryParameterTest {

    private static TelemetryParameter parameter(int bitOffset, int bitLength) {
        return new TelemetryParameter(
                "P", "Parameter", 100, ParameterDataType.UNSIGNED_INT, bitOffset, bitLength);
    }

    @Test
    void appliesLinearCalibration() {
        TelemetryParameter battery = parameter(0, 16);
        battery.setCalScale(0.001);
        battery.setCalOffset(0.0);

        // 28 000 raw millivolt counts -> 28.0 V
        assertThat(battery.calibrate(28_000)).isCloseTo(28.0, within(1e-9));
    }

    @Test
    void appliesOffsetAsWellAsScale() {
        TelemetryParameter sensor = parameter(0, 16);
        sensor.setCalScale(0.5);
        sensor.setCalOffset(-40.0);

        assertThat(sensor.calibrate(200)).isCloseTo(60.0, within(1e-9));
    }

    @Test
    void defaultsToIdentityCalibration() {
        assertThat(parameter(0, 8).calibrate(123)).isCloseTo(123.0, within(1e-9));
    }

    @Test
    void reportsAnExclusiveBitEnd() {
        assertThat(parameter(16, 16).bitEndExclusive()).isEqualTo(32);
    }

    /**
     * Adjacent fields must not be treated as overlapping: a parameter ending
     * at bit 32 exclusive and one starting at bit 32 are neighbours. Phase 3's
     * overlap detection depends on this boundary being exclusive.
     */
    @Test
    void adjacentParametersDoNotOverlap() {
        TelemetryParameter first = parameter(0, 16);
        TelemetryParameter second = parameter(16, 16);

        assertThat(first.bitEndExclusive()).isEqualTo(second.getBitOffset());
    }

    @Test
    void knowsWhichDataTypesAreNumeric() {
        assertThat(ParameterDataType.UNSIGNED_INT.isNumeric()).isTrue();
        assertThat(ParameterDataType.SIGNED_INT.isNumeric()).isTrue();
        assertThat(ParameterDataType.FLOAT.isNumeric()).isTrue();
        assertThat(ParameterDataType.ENUM.isNumeric()).isFalse();
        assertThat(ParameterDataType.BOOLEAN.isNumeric()).isFalse();
    }
}
