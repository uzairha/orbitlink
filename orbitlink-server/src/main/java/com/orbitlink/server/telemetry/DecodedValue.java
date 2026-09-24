package com.orbitlink.server.telemetry;

import com.orbitlink.server.dictionary.TelemetryParameter;

/**
 * One parameter pulled out of a packet.
 *
 * @param parameter   the dictionary definition used to decode it
 * @param rawValue    the field exactly as it arrived, before calibration
 * @param engValue    the calibrated value in engineering units
 * @param enumLabel   the label for an ENUM parameter, or null. Null for a
 *                    value with no matching state is meaningful — it says the
 *                    spacecraft reported a code the dictionary does not define.
 */
public record DecodedValue(
        TelemetryParameter parameter,
        long rawValue,
        double engValue,
        String enumLabel) {
}
