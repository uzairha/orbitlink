package com.orbitlink.server.dictionary;

/**
 * How the raw bits of a telemetry parameter should be interpreted.
 *
 * <p>Stored as a string in the database rather than an ordinal. Ordinals are
 * compact but positional: inserting a new constant in the middle of this enum
 * would silently reinterpret every row already written. The storage saving is
 * not worth that class of bug.
 */
public enum ParameterDataType {

    /** Unsigned integer, 1-32 bits, most significant bit first. */
    UNSIGNED_INT,

    /** Two's-complement signed integer, 2-32 bits. */
    SIGNED_INT,

    /** IEEE-754 float; bit length must be exactly 32 or 64. */
    FLOAT,

    /** Single bit, 0 or 1. */
    BOOLEAN,

    /** Unsigned integer whose values map to labels via telemetry_parameter_enum. */
    ENUM;

    /** True when a linear calibration can meaningfully be applied. */
    public boolean isNumeric() {
        return this == UNSIGNED_INT || this == SIGNED_INT || this == FLOAT;
    }
}
