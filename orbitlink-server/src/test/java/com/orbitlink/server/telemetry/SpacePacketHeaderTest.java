package com.orbitlink.server.telemetry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SpacePacketHeaderTest {

    /**
     * Header bytes the simulator produces for apid 100, sequence 0, 10-octet
     * payload.
     *
     * <p>identification = (version 0 << 13) | (type 0 << 12) | (sec 0 << 11)
     * | apid 100, which is 0x0064. Setting bit 11 here would mean "secondary
     * header present" while still reading as apid 100 — easy to write by
     * accident and invisible unless the flag is asserted separately.
     */
    private static final byte[] POWER_HEADER = {
            (byte) 0x00, (byte) 0x64,   // version 0, type 0, sec 0, apid 100
            (byte) 0xC0, (byte) 0x00,   // seq flags 3, count 0
            (byte) 0x00, (byte) 0x09    // length field: 10 octets minus one
    };

    @Test
    void parsesEveryPrimaryHeaderField() {
        SpacePacketHeader header = SpacePacketHeader.parse(POWER_HEADER);

        assertThat(header.versionNumber()).isZero();
        assertThat(header.packetType()).isZero();
        assertThat(header.secondaryHeaderFlag()).isZero();
        assertThat(header.apid()).isEqualTo(100);
        assertThat(header.sequenceFlags()).isEqualTo(3);
        assertThat(header.sequenceCount()).isZero();
    }

    /** The wire carries length minus one; parsing must add it back exactly once. */
    @Test
    void correctsTheMinusOneLengthEncoding() {
        assertThat(SpacePacketHeader.parse(POWER_HEADER).dataFieldLength()).isEqualTo(10);
    }

    @Test
    void aZeroLengthFieldMeansOneOctetOfPayload() {
        byte[] header = {0x00, 0x64, (byte) 0xC0, 0x00, 0x00, 0x00};

        assertThat(SpacePacketHeader.parse(header).dataFieldLength()).isEqualTo(1);
    }

    @Test
    void parsesTheLargestApid() {
        byte[] header = {0x07, (byte) 0xFF, (byte) 0xC0, 0x00, 0x00, 0x00};

        assertThat(SpacePacketHeader.parse(header).apid()).isEqualTo(2047);
    }

    @Test
    void parsesAFourteenBitSequenceCount() {
        byte[] header = {0x00, 0x64, (byte) 0xFF, (byte) 0xFF, 0x00, 0x00};

        SpacePacketHeader parsed = SpacePacketHeader.parse(header);
        assertThat(parsed.sequenceCount()).isEqualTo(16383);
        assertThat(parsed.sequenceFlags()).isEqualTo(3);
    }

    @Test
    void recognisesTelemetryPackets() {
        assertThat(SpacePacketHeader.parse(POWER_HEADER).isTelemetry()).isTrue();
    }

    /** Packet type 1 is a telecommand and must not be ingested as telemetry. */
    @Test
    void rejectsATelecommandPacket() {
        byte[] header = {0x10, 0x64, (byte) 0xC0, 0x00, 0x00, 0x00};

        SpacePacketHeader parsed = SpacePacketHeader.parse(header);
        assertThat(parsed.packetType()).isEqualTo(1);
        assertThat(parsed.isTelemetry()).isFalse();
    }

    @Test
    void rejectsAShortHeader() {
        assertThatThrownBy(() -> SpacePacketHeader.parse(new byte[5]))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
