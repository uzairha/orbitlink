package com.orbitlink.server.telemetry;

/**
 * A decoded CCSDS primary header.
 *
 * @param versionNumber       expected to be 0
 * @param packetType          0 telemetry, 1 telecommand
 * @param secondaryHeaderFlag whether a secondary header follows
 * @param apid                which packet this is, 0..2047
 * @param sequenceFlags       3 means unsegmented
 * @param sequenceCount       wraps at 16383, used to spot gaps
 * @param dataFieldLength     octets of payload, already corrected for the
 *                            minus-one encoding on the wire
 */
public record SpacePacketHeader(
        int versionNumber,
        int packetType,
        int secondaryHeaderFlag,
        int apid,
        int sequenceFlags,
        int sequenceCount,
        int dataFieldLength) {

    public static final int BYTES = 6;

    /**
     * Parses the 6-octet primary header.
     *
     * <p>The wire carries payload length minus one, so this adds it back. Every
     * caller then deals in real octet counts and cannot forget the adjustment.
     */
    public static SpacePacketHeader parse(byte[] header) {
        if (header == null || header.length < BYTES) {
            throw new IllegalArgumentException(
                    "primary header is " + BYTES + " octets");
        }

        int identification = word(header, 0);
        int sequenceControl = word(header, 2);
        int lengthField = word(header, 4);

        return new SpacePacketHeader(
                identification >> 13,
                (identification >> 12) & 0x1,
                (identification >> 11) & 0x1,
                identification & 0x7FF,
                sequenceControl >> 14,
                sequenceControl & 0x3FFF,
                lengthField + 1);
    }

    /** True for a well-formed telemetry packet from this mission's spacecraft. */
    public boolean isTelemetry() {
        return versionNumber == 0 && packetType == 0;
    }

    private static int word(byte[] bytes, int index) {
        return ((bytes[index] & 0xFF) << 8) | (bytes[index + 1] & 0xFF);
    }
}
