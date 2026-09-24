package com.orbitlink.simulator;

/**
 * Packs integer fields into a byte array at arbitrary bit offsets.
 *
 * <p>Bit 0 is the most significant bit of byte 0, which is the convention CCSDS
 * uses throughout. Writing big-endian like this means a 16-bit field at offset
 * 0 lands exactly where a reader expecting a big-endian short would look for
 * it, and a field that straddles a byte boundary still reads in the obvious
 * order.
 *
 * <p>Deliberately duplicated rather than shared with the server's decoder. The
 * simulator stands in for flight software: on a real programme the spacecraft
 * and the ground system are separate implementations that agree only on a
 * published format. Sharing a class here would let a bug cancel itself
 * out — the encoder and decoder would agree with each other while both
 * disagreed with the spec.
 */
public final class BitWriter {

    private final byte[] buffer;

    public BitWriter(int sizeInBytes) {
        this.buffer = new byte[sizeInBytes];
    }

    /**
     * Writes the low {@code bitLength} bits of {@code value} starting at
     * {@code bitOffset}.
     *
     * <p>Negative values are written in two's complement, truncated to the
     * field width — which is exactly how a signed field arrives on the wire.
     *
     * @throws IllegalArgumentException if the field does not fit the buffer
     */
    public void write(int bitOffset, int bitLength, long value) {
        if (bitLength <= 0 || bitLength > 32) {
            throw new IllegalArgumentException("bitLength must be 1..32, got " + bitLength);
        }
        if (bitOffset < 0) {
            throw new IllegalArgumentException("bitOffset must not be negative");
        }
        if (bitOffset + bitLength > buffer.length * 8) {
            throw new IllegalArgumentException(
                    "field at bit " + bitOffset + " length " + bitLength
                            + " exceeds buffer of " + (buffer.length * 8) + " bits");
        }

        // Mask to the field width so a negative (or oversized) value becomes
        // the correct unsigned bit pattern rather than smearing sign bits
        // across neighbouring fields.
        long masked = bitLength == 32
                ? value & 0xFFFF_FFFFL
                : value & ((1L << bitLength) - 1);

        for (int i = 0; i < bitLength; i++) {
            // Most significant bit of the field goes to the lowest bit index.
            long bit = (masked >> (bitLength - 1 - i)) & 1L;
            int absoluteBit = bitOffset + i;
            int byteIndex = absoluteBit / 8;
            int bitInByte = 7 - (absoluteBit % 8);

            if (bit == 1) {
                buffer[byteIndex] |= (byte) (1 << bitInByte);
            } else {
                buffer[byteIndex] &= (byte) ~(1 << bitInByte);
            }
        }
    }

    /** Returns a copy, so a caller cannot mutate this writer's buffer. */
    public byte[] toByteArray() {
        return buffer.clone();
    }

    public int sizeInBytes() {
        return buffer.length;
    }
}
