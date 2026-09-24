package com.orbitlink.server.telemetry;

import com.orbitlink.server.dictionary.ParameterEnumState;
import com.orbitlink.server.dictionary.TelemetryParameter;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Turns a packet data field into decoded parameter values, driven entirely by
 * the telemetry dictionary.
 *
 * <p>Pure and stateless: it takes bytes and parameter definitions and returns
 * values. No sockets, no database, no Spring. That keeps the part of ingestion
 * most likely to be subtly wrong — bit alignment, sign extension, calibration
 * — testable with plain unit tests.
 *
 * <p>A parameter that runs past the end of the received data field is skipped
 * with a warning rather than aborting the packet. Partial telemetry is still
 * useful, and a single over-long definition should not discard the other
 * parameters that decoded cleanly.
 */
public final class PacketDecoder {

    private static final Logger log = LoggerFactory.getLogger(PacketDecoder.class);

    private PacketDecoder() {
        // Static utility.
    }

    public static List<DecodedValue> decode(byte[] dataField, List<TelemetryParameter> parameters) {
        List<DecodedValue> values = new ArrayList<>(parameters.size());

        for (TelemetryParameter parameter : parameters) {
            if (parameter.bitEndExclusive() > dataField.length * 8) {
                log.warn("Parameter {} needs bits {}-{} but the data field is only {} bits; skipping",
                        parameter.getMnemonic(), parameter.getBitOffset(),
                        parameter.bitEndExclusive() - 1, dataField.length * 8);
                continue;
            }
            values.add(decodeOne(dataField, parameter));
        }
        return values;
    }

    private static DecodedValue decodeOne(byte[] dataField, TelemetryParameter parameter) {
        int offset = parameter.getBitOffset();
        int length = parameter.getBitLength();

        long raw = switch (parameter.getDataType()) {
            // Only SIGNED_INT is two's complement. Reading an unsigned field
            // as signed would turn a high battery voltage into a negative
            // number the moment the top bit sets.
            case SIGNED_INT -> BitReader.readSigned(dataField, offset, length);
            case UNSIGNED_INT, ENUM, BOOLEAN -> BitReader.readUnsigned(dataField, offset, length);
            case FLOAT -> BitReader.readUnsigned(dataField, offset, length);
        };

        double engineering;
        String enumLabel = null;

        switch (parameter.getDataType()) {
            case FLOAT -> {
                // The bits are an IEEE-754 pattern, not a count, so they are
                // reinterpreted rather than converted. Applying a scale factor
                // to the bit pattern first would be meaningless.
                double asFloat = length == 32
                        ? Float.intBitsToFloat((int) raw)
                        : Double.longBitsToDouble(raw);
                engineering = parameter.calibrate(asFloat);
            }
            case ENUM -> {
                engineering = raw;
                enumLabel = labelFor(parameter, raw);
            }
            // Calibration is meaningless for a flag, so the value passes
            // through as 0 or 1.
            case BOOLEAN -> engineering = raw;
            default -> engineering = parameter.calibrate(raw);
        }

        return new DecodedValue(parameter, raw, engineering, enumLabel);
    }

    /** Null when the spacecraft reported a code the dictionary does not define. */
    private static String labelFor(TelemetryParameter parameter, long raw) {
        for (ParameterEnumState state : parameter.getEnumStates()) {
            if (state.getRawValue() == raw) {
                return state.getLabel();
            }
        }
        log.warn("Parameter {} reported undefined enum value {}", parameter.getMnemonic(), raw);
        return null;
    }
}
