package com.orbitlink.server.telemetry;

/**
 * Extracts integer fields from a packet data field at arbitrary bit offsets.
 *
 * <p>Bit 0 is the most significant bit of byte 0, matching CCSDS convention and
 * the simulator's encoder — which is a separate implementation in a separate
 * module, by design. The two agree only on the published format.
 *
 * <p>Stateless and side-effect free, so the whole decode path is unit-testable
 * without a socket or a database.
 */
public final class BitReader {

    private BitReader() {
        // Static utility.
    }

    /**
     * Reads {@code bitLength} bits starting at {@code bitOffset} as an
     * unsigned value.
     */
    public static long readUnsigned(byte[] data, int bitOffset, int bitLength) {
        validate(data, bitOffset, bitLength);

        long value = 0;
        for (int i = 0; i < bitLength; i++) {
            int absoluteBit = bitOffset + i;
            int bit = (data[absoluteBit / 8] >> (7 - (absoluteBit % 8))) & 1;
            value = (value << 1) | bit;
        }
        return value;
    }

    /**
     * Reads a two's-complement signed field.
     *
     * <p>Sign extension is explicit rather than relying on a Java shift,
     * because the field is rarely a whole number of bytes: a 12-bit negative
     * value read as unsigned looks like a large positive number until the
     * sign bit is interpreted against <em>this field's</em> width.
     */
    public static long readSigned(byte[] data, int bitOffset, int bitLength) {
        long raw = readUnsigned(data, bitOffset, bitLength);
        long signBit = 1L << (bitLength - 1);
        return (raw & signBit) != 0 ? raw - (1L << bitLength) : raw;
    }

    private static void validate(byte[] data, int bitOffset, int bitLength) {
        if (bitLength <= 0 || bitLength > 32) {
            throw new IllegalArgumentException("bitLength must be 1..32, got " + bitLength);
        }
        if (bitOffset < 0) {
            throw new IllegalArgumentException("bitOffset must not be negative");
        }
        if (bitOffset + bitLength > data.length * 8) {
            throw new IllegalArgumentException(
                    "field at bit " + bitOffset + " length " + bitLength
                            + " runs past the end of a " + data.length + "-octet data field");
        }
    }
}
