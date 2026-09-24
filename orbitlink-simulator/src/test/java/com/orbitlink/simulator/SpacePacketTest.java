package com.orbitlink.simulator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Pins the CCSDS primary header against the specification by decoding the
 * header by hand, field by field, rather than by round-tripping through the
 * encoder. A round-trip test would pass happily even if the layout were wrong.
 */
class SpacePacketTest {

    private static int word(byte[] packet, int index) {
        return ((packet[index] & 0xFF) << 8) | (packet[index + 1] & 0xFF);
    }

    @Test
    void writesASixOctetHeaderBeforeThePayload() {
        byte[] packet = SpacePacket.encode(100, 0, new byte[] {1, 2, 3, 4});

        assertThat(packet).hasSize(SpacePacket.PRIMARY_HEADER_BYTES + 4);
        assertThat(new byte[] {packet[6], packet[7], packet[8], packet[9]})
                .containsExactly((byte) 1, (byte) 2, (byte) 3, (byte) 4);
    }

    @Test
    void encodesVersionTypeAndSecondaryHeaderFlagAsZero() {
        byte[] packet = SpacePacket.encode(100, 0, new byte[] {0});
        int identification = word(packet, 0);

        assertThat(identification >> 13).as("version number").isZero();
        assertThat((identification >> 12) & 0x1).as("packet type: 0 is telemetry").isZero();
        assertThat((identification >> 11) & 0x1).as("secondary header flag").isZero();
    }

    @Test
    void encodesTheApidInTheLowElevenBits() {
        byte[] packet = SpacePacket.encode(101, 0, new byte[] {0});

        assertThat(word(packet, 0) & 0x7FF).isEqualTo(101);
    }

    @Test
    void encodesTheLargestLegalApid() {
        byte[] packet = SpacePacket.encode(2047, 0, new byte[] {0});

        assertThat(word(packet, 0) & 0x7FF).isEqualTo(2047);
    }

    @Test
    void marksPacketsUnsegmented() {
        byte[] packet = SpacePacket.encode(100, 0, new byte[] {0});

        assertThat(word(packet, 2) >> 14).as("sequence flags").isEqualTo(0b11);
    }

    @Test
    void encodesTheSequenceCountInFourteenBits() {
        byte[] packet = SpacePacket.encode(100, 12345, new byte[] {0});

        assertThat(word(packet, 2) & 0x3FFF).isEqualTo(12345);
    }

    /**
     * The field carries octet count MINUS ONE. Getting this wrong by one is
     * the classic CCSDS mistake and would desynchronise the whole stream.
     */
    @Test
    void encodesPacketDataLengthAsOctetCountMinusOne() {
        byte[] packet = SpacePacket.encode(100, 0, new byte[10]);

        assertThat(word(packet, 4)).isEqualTo(9);
    }

    @Test
    void encodesLengthCorrectlyForASingleOctetPayload() {
        byte[] packet = SpacePacket.encode(100, 0, new byte[1]);

        assertThat(word(packet, 4)).isZero();
    }

    /**
     * A reader must be able to find the next packet using only the header.
     * This walks two concatenated packets the way phase 5's decoder will.
     */
    @Test
    void producesASelfFramingStream() {
        byte[] first = SpacePacket.encode(100, 0, new byte[10]);
        byte[] second = SpacePacket.encode(101, 1, new byte[7]);

        byte[] stream = new byte[first.length + second.length];
        System.arraycopy(first, 0, stream, 0, first.length);
        System.arraycopy(second, 0, stream, first.length, second.length);

        int cursor = 0;
        int firstLength = word(stream, cursor + 4) + 1;
        assertThat(firstLength).isEqualTo(10);
        cursor += SpacePacket.PRIMARY_HEADER_BYTES + firstLength;

        assertThat(word(stream, cursor) & 0x7FF).as("second packet apid").isEqualTo(101);
        assertThat(word(stream, cursor + 4) + 1).as("second packet length").isEqualTo(7);
        assertThat(cursor + SpacePacket.PRIMARY_HEADER_BYTES + 7).isEqualTo(stream.length);
    }

    @Test
    void rejectsAnApidThatDoesNotFitElevenBits() {
        assertThatThrownBy(() -> SpacePacket.encode(2048, 0, new byte[] {0}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("apid");
    }

    @Test
    void rejectsASequenceCountThatDoesNotFitFourteenBits() {
        assertThatThrownBy(() -> SpacePacket.encode(100, 16384, new byte[] {0}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sequenceCount");
    }

    /** A space packet may never have an empty data field. */
    @Test
    void rejectsAnEmptyPayload() {
        assertThatThrownBy(() -> SpacePacket.encode(100, 0, new byte[0]))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
