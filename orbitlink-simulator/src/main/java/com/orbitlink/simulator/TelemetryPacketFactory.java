package com.orbitlink.simulator;

/**
 * Encodes spacecraft state into the packet layouts the ground dictionary
 * describes.
 *
 * <p><strong>This class is the mirror image of the telemetry dictionary, and
 * nothing enforces that at compile time.</strong> The offsets and scale factors
 * below are written out again here rather than read from
 * {@code sample-dictionary.yaml}, because the simulator stands in for flight
 * software and flight software does not read the ground system's files. On a
 * real programme the spacecraft implements a published interface control
 * document and the ground dictionary describes the same document; the two are
 * kept in step by process, not by a shared import.
 *
 * <p>The cost is that a change on one side silently breaks the other. What
 * catches it is phase 5: if these offsets drift from the dictionary, decoded
 * values come out as nonsense — a battery reading 6553.5 V instead of 28.0 V
 * is an unmistakable symptom of a misaligned field.
 *
 * <p>Raw counts are the inverse of the dictionary's calibration. The ground
 * applies {@code engineering = raw * scale + offset}, so the spacecraft sends
 * {@code raw = (engineering - offset) / scale}.
 */
public final class TelemetryPacketFactory {

    public static final int APID_POWER = 100;
    public static final int APID_ATTITUDE = 101;

    /** APID 100 occupies 73 bits, which rounds up to 10 octets. */
    private static final int POWER_PAYLOAD_BYTES = 10;

    /** APID 101 occupies 56 bits, exactly 7 octets. */
    private static final int ATTITUDE_PAYLOAD_BYTES = 7;

    private TelemetryPacketFactory() {
        // Static factory only.
    }

    /** APID 100: electrical power and thermal housekeeping. */
    public static byte[] encodePowerPayload(SpacecraftState state) {
        BitWriter writer = new BitWriter(POWER_PAYLOAD_BYTES);

        // BATT_BUS_V: bits 0-15, unsigned, scale 0.001 -> raw is millivolts.
        writer.write(0, 16, Math.round(state.batteryVoltage() / 0.001));

        // BATT_CURRENT: bits 16-31, signed, scale 0.01 -> hundredths of an amp.
        writer.write(16, 16, Math.round(state.batteryCurrent() / 0.01));

        // BATT_TEMP: bits 32-47, signed, scale 0.01 -> hundredths of a degree.
        writer.write(32, 16, Math.round(state.batteryTemp() / 0.01));

        // SOLAR_ARRAY_PWR: bits 48-63, unsigned, scale 0.1 -> tenths of a watt.
        writer.write(48, 16, Math.round(state.solarArrayPower() / 0.1));

        // EPS_MODE: bits 64-71, enum.
        writer.write(64, 8, state.epsMode().rawValue());

        // HEATER_ON: bit 72, boolean. Bits 73-79 are unused padding to the
        // octet boundary; CCSDS data fields are whole octets.
        writer.write(72, 1, state.heaterOn() ? 1 : 0);

        return writer.toByteArray();
    }

    /** APID 101: attitude determination. */
    public static byte[] encodeAttitudePayload(SpacecraftState state) {
        BitWriter writer = new BitWriter(ATTITUDE_PAYLOAD_BYTES);

        // ATT_ROLL / PITCH / YAW: signed, scale 0.0055 deg per count.
        writer.write(0, 16, Math.round(state.roll() / 0.0055));
        writer.write(16, 16, Math.round(state.pitch() / 0.0055));
        writer.write(32, 16, Math.round(state.yaw() / 0.0055));

        // GYRO_HEALTH: bits 48-55, enum.
        writer.write(48, 8, state.gyroHealth().rawValue());

        return writer.toByteArray();
    }
}
