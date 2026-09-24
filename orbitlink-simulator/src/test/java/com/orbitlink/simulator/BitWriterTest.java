package com.orbitlink.simulator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class BitWriterTest {

    @Test
    void writesAByteAlignedField() {
        BitWriter writer = new BitWriter(2);
        writer.write(0, 16, 0xABCD);

        assertThat(writer.toByteArray()).containsExactly((byte) 0xAB, (byte) 0xCD);
    }

    /** Bit 0 is the most significant bit of byte 0, per CCSDS convention. */
    @Test
    void writesBitZeroAsTheMostSignificantBit() {
        BitWriter writer = new BitWriter(1);
        writer.write(0, 1, 1);

        assertThat(writer.toByteArray()).containsExactly((byte) 0x80);
    }

    @Test
    void writesASingleBitAtAnOffset() {
        BitWriter writer = new BitWriter(1);
        writer.write(7, 1, 1);

        assertThat(writer.toByteArray()).containsExactly((byte) 0x01);
    }

    @Test
    void writesAFieldStraddlingAByteBoundary() {
        BitWriter writer = new BitWriter(2);
        // 4 bits at offset 6 spans the last two bits of byte 0 and the first
        // two of byte 1. 0b1011 -> 0b10 into byte 0, 0b11 into byte 1.
        writer.write(6, 4, 0b1011);

        assertThat(writer.toByteArray()).containsExactly((byte) 0b0000_0010, (byte) 0b1100_0000);
    }

    /** Negative values go on the wire as two's complement, truncated to width. */
    @Test
    void writesNegativeValuesAsTwosComplement() {
        BitWriter writer = new BitWriter(2);
        writer.write(0, 16, -1);

        assertThat(writer.toByteArray()).containsExactly((byte) 0xFF, (byte) 0xFF);
    }

    @Test
    void writesASmallNegativeValue() {
        BitWriter writer = new BitWriter(2);
        writer.write(0, 16, -2);

        assertThat(writer.toByteArray()).containsExactly((byte) 0xFF, (byte) 0xFE);
    }

    /**
     * A value wider than its field must not bleed into neighbouring fields —
     * that would silently corrupt an adjacent parameter.
     */
    @Test
    void masksValuesToTheFieldWidth() {
        BitWriter writer = new BitWriter(2);
        writer.write(8, 8, 0x1FF);

        assertThat(writer.toByteArray()).containsExactly((byte) 0x00, (byte) 0xFF);
    }

    @Test
    void adjacentFieldsDoNotDisturbEachOther() {
        BitWriter writer = new BitWriter(4);
        writer.write(0, 16, 0x1234);
        writer.write(16, 16, 0x5678);

        assertThat(writer.toByteArray())
                .containsExactly((byte) 0x12, (byte) 0x34, (byte) 0x56, (byte) 0x78);
    }

    @Test
    void rejectsAFieldThatOverrunsTheBuffer() {
        BitWriter writer = new BitWriter(1);

        assertThatThrownBy(() -> writer.write(4, 8, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds buffer");
    }

    @Test
    void rejectsAnInvalidBitLength() {
        BitWriter writer = new BitWriter(8);

        assertThatThrownBy(() -> writer.write(0, 0, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> writer.write(0, 33, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void returnsADefensiveCopy() {
        BitWriter writer = new BitWriter(1);
        writer.write(0, 8, 0xFF);

        byte[] first = writer.toByteArray();
        first[0] = 0x00;

        assertThat(writer.toByteArray()).containsExactly((byte) 0xFF);
    }
}
