package com.orbitlink.simulator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;
import org.junit.jupiter.api.Test;

/**
 * Decodes the simulator's own payloads by hand and applies the telemetry
 * dictionary's calibration, checking the result lands in the engineering range
 * the dictionary declares.
 *
 * <p>This is the closest thing to a contract test between the two modules while
 * they remain independent. Nothing stops the offsets here drifting from
 * sample-dictionary.yaml, so this test encodes the dictionary's numbers
 * separately and fails loudly if the encoder stops agreeing with them.
 */
class TelemetryPacketFactoryTest {

    /** Reads an unsigned field, mirroring what the ground decoder will do. */
    private static long readUnsigned(byte[] data, int bitOffset, int bitLength) {
        long value = 0;
        for (int i = 0; i < bitLength; i++) {
            int absoluteBit = bitOffset + i;
            int bit = (data[absoluteBit / 8] >> (7 - (absoluteBit % 8))) & 1;
            value = (value << 1) | bit;
        }
        return value;
    }

    /** Reads a two's-complement signed field. */
    private static long readSigned(byte[] data, int bitOffset, int bitLength) {
        long raw = readUnsigned(data, bitOffset, bitLength);
        long signBit = 1L << (bitLength - 1);
        return (raw & signBit) != 0 ? raw - (1L << bitLength) : raw;
    }

    private static SpacecraftState settledState() {
        SpacecraftState state = new SpacecraftState(
                RandomGeneratorFactory.of("L64X128MixRandom").create(42L));
        for (int i = 0; i < 50; i++) {
            state.advance(0.5);
        }
        return state;
    }

    @Test
    void powerPayloadIsTenOctets() {
        assertThat(TelemetryPacketFactory.encodePowerPayload(settledState())).hasSize(10);
    }

    @Test
    void attitudePayloadIsSevenOctets() {
        assertThat(TelemetryPacketFactory.encodeAttitudePayload(settledState())).hasSize(7);
    }

    @Test
    void batteryVoltageDecodesBackToTheModelledValue() {
        SpacecraftState state = settledState();
        byte[] payload = TelemetryPacketFactory.encodePowerPayload(state);

        // BATT_BUS_V: bits 0-15, unsigned, scale 0.001
        double volts = readUnsigned(payload, 0, 16) * 0.001;

        assertThat(volts).isCloseTo(state.batteryVoltage(), within(0.001));
        assertThat(volts).as("within the dictionary's declared range").isBetween(22.0, 34.0);
    }

    @Test
    void batteryCurrentSurvivesTheSignedRoundTrip() {
        SpacecraftState state = settledState();
        byte[] payload = TelemetryPacketFactory.encodePowerPayload(state);

        // BATT_CURRENT: bits 16-31, signed, scale 0.01
        double amps = readSigned(payload, 16, 16) * 0.01;

        assertThat(amps).isCloseTo(state.batteryCurrent(), within(0.01));
    }

    @Test
    void batteryTemperatureDecodesCorrectly() {
        SpacecraftState state = settledState();
        byte[] payload = TelemetryPacketFactory.encodePowerPayload(state);

        double degC = readSigned(payload, 32, 16) * 0.01;

        assertThat(degC).isCloseTo(state.batteryTemp(), within(0.01));
    }

    @Test
    void solarArrayPowerDecodesCorrectly() {
        SpacecraftState state = settledState();
        byte[] payload = TelemetryPacketFactory.encodePowerPayload(state);

        double watts = readUnsigned(payload, 48, 16) * 0.1;

        assertThat(watts).isCloseTo(state.solarArrayPower(), within(0.1));
        assertThat(watts).isBetween(0.0, 450.0);
    }

    @Test
    void epsModeAndHeaterFlagLandInTheirOwnFields() {
        SpacecraftState state = settledState();
        byte[] payload = TelemetryPacketFactory.encodePowerPayload(state);

        assertThat(readUnsigned(payload, 64, 8))
                .isEqualTo(state.epsMode().rawValue());
        assertThat(readUnsigned(payload, 72, 1))
                .isEqualTo(state.heaterOn() ? 1 : 0);
    }

    @Test
    void attitudeAnglesDecodeWithinDeclaredRanges() {
        SpacecraftState state = settledState();
        byte[] payload = TelemetryPacketFactory.encodeAttitudePayload(state);

        double roll = readSigned(payload, 0, 16) * 0.0055;
        double pitch = readSigned(payload, 16, 16) * 0.0055;
        double yaw = readSigned(payload, 32, 16) * 0.0055;

        assertThat(roll).isCloseTo(state.roll(), within(0.0055));
        assertThat(pitch).isCloseTo(state.pitch(), within(0.0055));
        assertThat(yaw).isCloseTo(state.yaw(), within(0.0055));

        assertThat(roll).isBetween(-180.0, 180.0);
        assertThat(pitch).isBetween(-90.0, 90.0);
        assertThat(yaw).isBetween(-180.0, 180.0);
    }

    @Test
    void gyroHealthLandsInItsOwnField() {
        SpacecraftState state = settledState();
        byte[] payload = TelemetryPacketFactory.encodeAttitudePayload(state);

        assertThat(readUnsigned(payload, 48, 8)).isEqualTo(state.gyroHealth().rawValue());
    }

    /**
     * Attitude at full scale must still fit a signed 16-bit field: 180 / 0.0055
     * is 32727, just inside the 32767 limit. A coarser scale factor would
     * silently wrap the angle.
     */
    @Test
    void fullScaleAttitudeFitsInSixteenSignedBits() {
        assertThat(Math.round(180.0 / 0.0055)).isLessThanOrEqualTo(32767);
        assertThat(Math.round(-180.0 / 0.0055)).isGreaterThanOrEqualTo(-32768);
    }

    /** An injected fault must actually leave the dictionary's declared range. */
    @Test
    void injectedFaultsProduceOutOfLimitValues() {
        RandomGenerator random = RandomGeneratorFactory.of("L64X128MixRandom").create(7L);

        boolean sawOutOfLimits = false;
        for (int attempt = 0; attempt < 40 && !sawOutOfLimits; attempt++) {
            SpacecraftState state = settledState();
            state.injectFault(random);

            byte[] payload = TelemetryPacketFactory.encodePowerPayload(state);
            double volts = readUnsigned(payload, 0, 16) * 0.001;
            double degC = readSigned(payload, 32, 16) * 0.01;

            if (volts < 22.0 || volts > 34.0 || degC < -20.0 || degC > 60.0) {
                sawOutOfLimits = true;
            }
        }

        assertThat(sawOutOfLimits)
                .as("fault injection should drive a parameter outside its limits")
                .isTrue();
    }

    /** The model must stay physically plausible on its own, without faults. */
    @Test
    void normalOperationStaysWithinLimits() {
        SpacecraftState state = new SpacecraftState(
                RandomGeneratorFactory.of("L64X128MixRandom").create(99L));

        for (int i = 0; i < 600; i++) {
            state.advance(0.5);
            byte[] payload = TelemetryPacketFactory.encodePowerPayload(state);

            double volts = readUnsigned(payload, 0, 16) * 0.001;
            double degC = readSigned(payload, 32, 16) * 0.01;

            assertThat(volts).isBetween(22.0, 34.0);
            assertThat(degC).isBetween(-20.0, 60.0);
        }
    }
}
