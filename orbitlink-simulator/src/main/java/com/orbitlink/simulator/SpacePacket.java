package com.orbitlink.simulator;

/**
 * A CCSDS Space Packet (CCSDS 133.0-B, the Space Packet Protocol).
 *
 * <p>The 6-octet primary header is laid out as three big-endian 16-bit words:
 *
 * <pre>
 *  octet 0-1  packet identification
 *             bits  0-2   version number      (000)
 *             bit   3     packet type         (0 = telemetry, 1 = telecommand)
 *             bit   4     secondary header flag
 *             bits  5-15  APID                (11 bits, 0..2047)
 *
 *  octet 2-3  packet sequence control
 *             bits  0-1   sequence flags      (11 = unsegmented)
 *             bits  2-15  sequence count      (14 bits, wraps at 16383)
 *
 *  octet 4-5  packet data length
 *             number of octets in the data field, MINUS ONE
 * </pre>
 *
 * <p>That minus-one on the length is the classic place to get CCSDS wrong. It
 * exists because a packet may never have an empty data field, so the encoding
 * would otherwise waste its zero value. A 10-octet payload is written as 9.
 *
 * <p>The length field is also what makes a packet stream self-framing: a
 * reader takes 6 octets, learns the length, and knows exactly how many more to
 * consume before the next header begins. Phase 5's TCP decoder depends on
 * that, because TCP is a byte stream with no message boundaries of its own.
 */
public final class SpacePacket {

    public static final int PRIMARY_HEADER_BYTES = 6;

    /** Sequence count is 14 bits, so it rolls over rather than growing. */
    public static final int MAX_SEQUENCE_COUNT = 0x3FFF;

    public static final int MAX_APID = 0x7FF;

    private static final int VERSION_TELEMETRY = 0;
    private static final int TYPE_TELEMETRY = 0;
    private static final int SEQUENCE_FLAGS_UNSEGMENTED = 0b11;

    private SpacePacket() {
        // Static factory only.
    }

    /**
     * Builds a complete telemetry packet: primary header followed by payload.
     *
     * @param apid          application process identifier, 0..2047
     * @param sequenceCount packet sequence count, 0..16383
     * @param payload       the packet data field
     */
    public static byte[] encode(int apid, int sequenceCount, byte[] payload) {
        if (apid < 0 || apid > MAX_APID) {
            throw new IllegalArgumentException("apid must be 0.." + MAX_APID + ", got " + apid);
        }
        if (sequenceCount < 0 || sequenceCount > MAX_SEQUENCE_COUNT) {
            throw new IllegalArgumentException(
                    "sequenceCount must be 0.." + MAX_SEQUENCE_COUNT + ", got " + sequenceCount);
        }
        if (payload == null || payload.length == 0) {
            throw new IllegalArgumentException("a space packet must carry a non-empty data field");
        }

        byte[] packet = new byte[PRIMARY_HEADER_BYTES + payload.length];

        int identification = (VERSION_TELEMETRY << 13)
                | (TYPE_TELEMETRY << 12)
                // No secondary header: this demo carries no CCSDS time code,
                // so the ground system timestamps on receipt instead.
                | (0 << 11)
                | (apid & MAX_APID);

        int sequenceControl = (SEQUENCE_FLAGS_UNSEGMENTED << 14)
                | (sequenceCount & MAX_SEQUENCE_COUNT);

        int dataLengthField = payload.length - 1;

        packet[0] = (byte) (identification >> 8);
        packet[1] = (byte) identification;
        packet[2] = (byte) (sequenceControl >> 8);
        packet[3] = (byte) sequenceControl;
        packet[4] = (byte) (dataLengthField >> 8);
        packet[5] = (byte) dataLengthField;

        System.arraycopy(payload, 0, packet, PRIMARY_HEADER_BYTES, payload.length);
        return packet;
    }
}
