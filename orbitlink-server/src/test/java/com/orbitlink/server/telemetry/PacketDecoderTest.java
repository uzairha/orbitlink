package com.orbitlink.server.telemetry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.orbitlink.server.dictionary.ParameterDataType;
import com.orbitlink.server.dictionary.ParameterEnumState;
import com.orbitlink.server.dictionary.TelemetryParameter;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Decoding tests, including a cross-module check.
 *
 * <p>The simulator and the server share no code by design, so
 * {@link #decodesAPacketBuiltExactlyAsTheSimulatorBuildsIt()} hand-builds the
 * bytes the simulator would produce and asserts the server recovers the
 * original engineering values. That is the contract between the two modules,
 * and it is the test that catches drift between the encoder and the dictionary.
 */
class PacketDecoderTest {

    private static TelemetryParameter parameter(
            String mnemonic, ParameterDataType type, int offset, int length,
            double scale, double calOffset) {
        TelemetryParameter p =
                new TelemetryParameter(mnemonic, mnemonic, 100, type, offset, length);
        p.setCalScale(scale);
        p.setCalOffset(calOffset);
        return p;
    }

    @Test
    void decodesAnUnsignedFieldAndAppliesCalibration() {
        // 28 000 millivolts at scale 0.001 -> 28.0 V
        byte[] data = {(byte) 0x6D, (byte) 0x60};
        TelemetryParameter battV =
                parameter("BATT_BUS_V", ParameterDataType.UNSIGNED_INT, 0, 16, 0.001, 0.0);

        DecodedValue value = PacketDecoder.decode(data, List.of(battV)).get(0);

        assertThat(value.rawValue()).isEqualTo(28_000);
        assertThat(value.engValue()).isCloseTo(28.0, within(1e-9));
    }

    /** A signed field whose top bit is set must come back negative. */
    @Test
    void decodesANegativeSignedField() {
        byte[] data = {(byte) 0xFF, (byte) 0xFF};
        TelemetryParameter current =
                parameter("BATT_CURRENT", ParameterDataType.SIGNED_INT, 0, 16, 0.01, 0.0);

        DecodedValue value = PacketDecoder.decode(data, List.of(current)).get(0);

        assertThat(value.rawValue()).isEqualTo(-1);
        assertThat(value.engValue()).isCloseTo(-0.01, within(1e-9));
    }

    /**
     * The same bytes read as unsigned give a completely different answer.
     * Treating a signed field as unsigned is the single most likely decode bug,
     * so it is pinned explicitly.
     */
    @Test
    void theSameBitsDecodeDifferentlyAsSignedAndUnsigned() {
        byte[] data = {(byte) 0xFF, (byte) 0x9C};

        TelemetryParameter signed =
                parameter("S", ParameterDataType.SIGNED_INT, 0, 16, 1.0, 0.0);
        TelemetryParameter unsigned =
                parameter("U", ParameterDataType.UNSIGNED_INT, 0, 16, 1.0, 0.0);

        assertThat(PacketDecoder.decode(data, List.of(signed)).get(0).rawValue()).isEqualTo(-100);
        assertThat(PacketDecoder.decode(data, List.of(unsigned)).get(0).rawValue()).isEqualTo(65_436);
    }

    @Test
    void appliesOffsetAsWellAsScale() {
        byte[] data = {(byte) 0x00, (byte) 0xC8};
        TelemetryParameter sensor =
                parameter("SENSOR", ParameterDataType.UNSIGNED_INT, 0, 16, 0.5, -40.0);

        assertThat(PacketDecoder.decode(data, List.of(sensor)).get(0).engValue())
                .isCloseTo(60.0, within(1e-9));
    }

    @Test
    void resolvesEnumLabels() {
        byte[] data = {(byte) 0x02};
        TelemetryParameter mode = parameter("EPS_MODE", ParameterDataType.ENUM, 0, 8, 1.0, 0.0);
        mode.addEnumState(new ParameterEnumState(0, "NOMINAL"));
        mode.addEnumState(new ParameterEnumState(2, "SAFE_MODE"));

        DecodedValue value = PacketDecoder.decode(data, List.of(mode)).get(0);

        assertThat(value.rawValue()).isEqualTo(2);
        assertThat(value.enumLabel()).isEqualTo("SAFE_MODE");
    }

    /** An undefined code must surface as null, not as a wrong label. */
    @Test
    void leavesTheLabelNullForAnUndefinedEnumValue() {
        byte[] data = {(byte) 0x07};
        TelemetryParameter mode = parameter("EPS_MODE", ParameterDataType.ENUM, 0, 8, 1.0, 0.0);
        mode.addEnumState(new ParameterEnumState(0, "NOMINAL"));

        DecodedValue value = PacketDecoder.decode(data, List.of(mode)).get(0);

        assertThat(value.rawValue()).isEqualTo(7);
        assertThat(value.enumLabel()).isNull();
    }

    /** Calibration must not be applied to a flag. */
    @Test
    void passesBooleansThroughUncalibrated() {
        byte[] data = {(byte) 0x80};
        TelemetryParameter heater =
                parameter("HEATER_ON", ParameterDataType.BOOLEAN, 0, 1, 0.001, 5.0);

        DecodedValue value = PacketDecoder.decode(data, List.of(heater)).get(0);

        assertThat(value.rawValue()).isEqualTo(1);
        assertThat(value.engValue()).isEqualTo(1.0);
    }

    /** One over-long definition must not discard the parameters that decoded. */
    @Test
    void skipsParametersRunningPastTheDataFieldButKeepsTheRest() {
        byte[] data = {(byte) 0x12, (byte) 0x34};

        TelemetryParameter good =
                parameter("GOOD", ParameterDataType.UNSIGNED_INT, 0, 16, 1.0, 0.0);
        TelemetryParameter tooLong =
                parameter("TOO_LONG", ParameterDataType.UNSIGNED_INT, 16, 16, 1.0, 0.0);

        List<DecodedValue> values = PacketDecoder.decode(data, List.of(good, tooLong));

        assertThat(values).hasSize(1);
        assertThat(values.get(0).parameter().getMnemonic()).isEqualTo("GOOD");
    }

    /**
     * Cross-module contract: bytes built the way the simulator builds them,
     * decoded by the server, must yield the original engineering values.
     */
    @Test
    void decodesAPacketBuiltExactlyAsTheSimulatorBuildsIt() {
        // Values the spacecraft model would hold.
        double volts = 27.995;
        double amps = -3.21;
        double degC = 17.83;
        double watts = 254.6;

        byte[] payload = new byte[10];
        writeBits(payload, 0, 16, Math.round(volts / 0.001));
        writeBits(payload, 16, 16, Math.round(amps / 0.01));
        writeBits(payload, 32, 16, Math.round(degC / 0.01));
        writeBits(payload, 48, 16, Math.round(watts / 0.1));
        writeBits(payload, 64, 8, 0);   // EPS_MODE NOMINAL
        writeBits(payload, 72, 1, 1);   // HEATER_ON

        TelemetryParameter battV =
                parameter("BATT_BUS_V", ParameterDataType.UNSIGNED_INT, 0, 16, 0.001, 0.0);
        TelemetryParameter battI =
                parameter("BATT_CURRENT", ParameterDataType.SIGNED_INT, 16, 16, 0.01, 0.0);
        TelemetryParameter battT =
                parameter("BATT_TEMP", ParameterDataType.SIGNED_INT, 32, 16, 0.01, 0.0);
        TelemetryParameter arrayW =
                parameter("SOLAR_ARRAY_PWR", ParameterDataType.UNSIGNED_INT, 48, 16, 0.1, 0.0);
        TelemetryParameter epsMode =
                parameter("EPS_MODE", ParameterDataType.ENUM, 64, 8, 1.0, 0.0);
        epsMode.addEnumState(new ParameterEnumState(0, "NOMINAL"));
        TelemetryParameter heater =
                parameter("HEATER_ON", ParameterDataType.BOOLEAN, 72, 1, 1.0, 0.0);

        List<DecodedValue> values = PacketDecoder.decode(
                payload, List.of(battV, battI, battT, arrayW, epsMode, heater));

        assertThat(values).hasSize(6);
        assertThat(values.get(0).engValue()).isCloseTo(volts, within(0.001));
        assertThat(values.get(1).engValue()).isCloseTo(amps, within(0.01));
        assertThat(values.get(2).engValue()).isCloseTo(degC, within(0.01));
        assertThat(values.get(3).engValue()).isCloseTo(watts, within(0.1));
        assertThat(values.get(4).enumLabel()).isEqualTo("NOMINAL");
        assertThat(values.get(5).engValue()).isEqualTo(1.0);
    }

    /** Local copy of the simulator's bit packing, so this test depends on no other module. */
    private static void writeBits(byte[] buffer, int bitOffset, int bitLength, long value) {
        long masked = value & ((1L << bitLength) - 1);
        for (int i = 0; i < bitLength; i++) {
            long bit = (masked >> (bitLength - 1 - i)) & 1L;
            int absoluteBit = bitOffset + i;
            if (bit == 1) {
                buffer[absoluteBit / 8] |= (byte) (1 << (7 - (absoluteBit % 8)));
            }
        }
    }
}
