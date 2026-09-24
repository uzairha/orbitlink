package com.orbitlink.server.dictionary.validation;

import com.orbitlink.server.dictionary.ParameterDataType;
import com.orbitlink.server.dictionary.yaml.DictionaryFile;
import com.orbitlink.server.dictionary.yaml.EnumStateDefinition;
import com.orbitlink.server.dictionary.yaml.ParameterDefinition;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.springframework.stereotype.Service;

/**
 * Checks a parsed dictionary for definition mistakes.
 *
 * <p>Operates on the parsed YAML model, never on JPA entities, so it can run
 * <em>before</em> anything is written. That ordering is what lets the database
 * keep real constraints: by the time rows are inserted they are already known
 * good, and a caller can still dry-run a candidate file and get a full report
 * without side effects.
 *
 * <p>Collects every finding instead of throwing on the first. Fixing a
 * dictionary is an editing task, and an editor wants the whole list at once.
 */
@Service
public class DictionaryValidator {

    /**
     * Units the ground system recognises. Unknown units are a WARNING rather
     * than an ERROR: missions legitimately carry instrument-specific units,
     * and refusing to load a dictionary over an unrecognised string would be
     * more damaging than flagging it.
     */
    private static final Set<String> KNOWN_UNITS = Set.of(
            "V", "mV", "A", "mA", "W", "mW", "Ah", "J",
            "degC", "K",
            "deg", "rad", "deg/s", "rad/s",
            "m", "km", "m/s", "km/s", "m/s^2", "g",
            "Pa", "kPa", "bar", "N", "Nm", "T", "nT", "Gauss",
            "Hz", "kHz", "MHz", "s", "ms", "us",
            "dB", "dBm", "bps", "kbps",
            "%", "count", "unitless");

    /** Maximum bit width the decoder will support for an integer field. */
    private static final int MAX_INTEGER_BITS = 32;

    public ValidationReport validate(DictionaryFile file) {
        List<ValidationFinding> findings = new ArrayList<>();
        List<ParameterDefinition> parameters =
                file.parameters() == null ? List.of() : file.parameters();

        checkRequiredFields(parameters, findings);
        checkDuplicates(parameters, findings);
        checkLimits(parameters, findings);
        checkUnits(parameters, findings);
        checkBitFieldWidths(parameters, findings);
        checkEnumReferences(parameters, findings);
        checkCalibration(parameters, findings);
        checkBitFieldOverlaps(parameters, findings);

        return ValidationReport.of(file.version(), findings);
    }

    // --- individual rules ----------------------------------------------------

    private void checkRequiredFields(
            List<ParameterDefinition> parameters, List<ValidationFinding> findings) {
        for (ParameterDefinition p : parameters) {
            String id = identify(p);
            if (isBlank(p.mnemonic())) {
                findings.add(ValidationFinding.error(
                        "MISSING_MNEMONIC", id, "parameter has no mnemonic"));
            }
            if (isBlank(p.name())) {
                findings.add(ValidationFinding.error(
                        "MISSING_NAME", id, "parameter has no name"));
            }
            if (p.apid() == null) {
                findings.add(ValidationFinding.error(
                        "MISSING_APID", id, "parameter has no apid"));
            }
            if (p.dataType() == null) {
                findings.add(ValidationFinding.error(
                        "MISSING_DATA_TYPE", id, "parameter has no dataType"));
            }
            if (p.bitOffset() == null) {
                findings.add(ValidationFinding.error(
                        "MISSING_BIT_OFFSET", id, "parameter has no bitOffset"));
            } else if (p.bitOffset() < 0) {
                findings.add(ValidationFinding.error("NEGATIVE_BIT_OFFSET", id,
                        "bitOffset is negative: " + p.bitOffset()));
            }
            if (p.bitLength() == null) {
                findings.add(ValidationFinding.error(
                        "MISSING_BIT_LENGTH", id, "parameter has no bitLength"));
            } else if (p.bitLength() <= 0) {
                findings.add(ValidationFinding.error("NON_POSITIVE_BIT_LENGTH", id,
                        "bitLength must be greater than zero, got " + p.bitLength()));
            }
        }
    }

    /**
     * Duplicate mnemonics are an ERROR: the mnemonic is the lookup key, so two
     * parameters sharing one makes decoded telemetry ambiguous. Duplicate
     * display names are only a WARNING — confusing on a screen, but harmless
     * to the decoder.
     */
    private void checkDuplicates(
            List<ParameterDefinition> parameters, List<ValidationFinding> findings) {
        Map<String, Integer> mnemonicCounts = new HashMap<>();
        Map<String, Integer> nameCounts = new HashMap<>();

        for (ParameterDefinition p : parameters) {
            if (!isBlank(p.mnemonic())) {
                mnemonicCounts.merge(p.mnemonic(), 1, Integer::sum);
            }
            if (!isBlank(p.name())) {
                nameCounts.merge(p.name(), 1, Integer::sum);
            }
        }

        mnemonicCounts.forEach((mnemonic, count) -> {
            if (count > 1) {
                findings.add(ValidationFinding.error("DUPLICATE_MNEMONIC", mnemonic,
                        "mnemonic is defined " + count + " times"));
            }
        });
        nameCounts.forEach((name, count) -> {
            if (count > 1) {
                findings.add(ValidationFinding.warning("DUPLICATE_NAME", null,
                        "name \"" + name + "\" is used by " + count + " parameters"));
            }
        });
    }

    private void checkLimits(
            List<ParameterDefinition> parameters, List<ValidationFinding> findings) {
        for (ParameterDefinition p : parameters) {
            String id = identify(p);
            if (p.minValue() != null && p.maxValue() != null
                    && p.minValue() > p.maxValue()) {
                findings.add(ValidationFinding.error("INVERTED_LIMITS", id,
                        "minValue " + p.minValue() + " is greater than maxValue " + p.maxValue()));
            }
            if (p.warnLow() != null && p.warnHigh() != null
                    && p.warnLow() > p.warnHigh()) {
                findings.add(ValidationFinding.error("INVERTED_WARNING_LIMITS", id,
                        "warnLow " + p.warnLow() + " is greater than warnHigh " + p.warnHigh()));
            }

            // Warning limits must sit inside the critical pair. A warning band
            // outside the critical one can never be reached: the value trips
            // CRITICAL first, so the warning is dead configuration and almost
            // always means the two pairs were swapped.
            if (p.warnLow() != null && p.minValue() != null && p.warnLow() < p.minValue()) {
                findings.add(ValidationFinding.error("WARNING_OUTSIDE_CRITICAL", id,
                        "warnLow " + p.warnLow() + " is below the critical low " + p.minValue()
                                + ", so it can never trigger"));
            }
            if (p.warnHigh() != null && p.maxValue() != null && p.warnHigh() > p.maxValue()) {
                findings.add(ValidationFinding.error("WARNING_OUTSIDE_CRITICAL", id,
                        "warnHigh " + p.warnHigh() + " is above the critical high " + p.maxValue()
                                + ", so it can never trigger"));
            }
            // A warning limit with no critical counterpart is legal but worth
            // noting: the parameter can go yellow but never red on that side.
            if (p.warnLow() != null && p.minValue() == null) {
                findings.add(ValidationFinding.warning("WARNING_WITHOUT_CRITICAL", id,
                        "warnLow is defined with no critical low limit"));
            }
            if (p.warnHigh() != null && p.maxValue() == null) {
                findings.add(ValidationFinding.warning("WARNING_WITHOUT_CRITICAL", id,
                        "warnHigh is defined with no critical high limit"));
            }
            // Limits on a non-numeric parameter cannot be evaluated: there is
            // no ordering over enum labels or a boolean.
            if (p.dataType() != null && !p.dataType().isNumeric()
                    && (p.minValue() != null || p.maxValue() != null
                        || p.warnLow() != null || p.warnHigh() != null)) {
                findings.add(ValidationFinding.warning("LIMITS_ON_NON_NUMERIC", id,
                        "limits are defined on a " + p.dataType() + " parameter and will be ignored"));
            }
        }
    }

    private void checkUnits(
            List<ParameterDefinition> parameters, List<ValidationFinding> findings) {
        for (ParameterDefinition p : parameters) {
            String id = identify(p);
            if (isBlank(p.units())) {
                continue;
            }
            if (!KNOWN_UNITS.contains(p.units())) {
                findings.add(ValidationFinding.warning("UNKNOWN_UNIT", id,
                        "unit \"" + p.units() + "\" is not a recognised unit"));
            }
            if (p.dataType() != null && !p.dataType().isNumeric()) {
                findings.add(ValidationFinding.warning("UNIT_ON_NON_NUMERIC", id,
                        "unit \"" + p.units() + "\" on a " + p.dataType() + " parameter is meaningless"));
            }
        }
    }

    /** Each data type constrains how wide its field may be. */
    private void checkBitFieldWidths(
            List<ParameterDefinition> parameters, List<ValidationFinding> findings) {
        for (ParameterDefinition p : parameters) {
            if (p.dataType() == null || p.bitLength() == null || p.bitLength() <= 0) {
                continue; // already reported by checkRequiredFields
            }
            String id = identify(p);
            int bits = p.bitLength();

            switch (p.dataType()) {
                case BOOLEAN -> {
                    if (bits != 1) {
                        findings.add(ValidationFinding.error("BAD_BOOLEAN_WIDTH", id,
                                "BOOLEAN must be exactly 1 bit, got " + bits));
                    }
                }
                case FLOAT -> {
                    if (bits != 32 && bits != 64) {
                        findings.add(ValidationFinding.error("BAD_FLOAT_WIDTH", id,
                                "FLOAT must be 32 or 64 bits, got " + bits));
                    }
                }
                case SIGNED_INT -> {
                    // A one-bit two's-complement field can only represent 0
                    // and -1, which is never what anyone means.
                    if (bits < 2) {
                        findings.add(ValidationFinding.error("BAD_SIGNED_INT_WIDTH", id,
                                "SIGNED_INT needs at least 2 bits, got " + bits));
                    } else if (bits > MAX_INTEGER_BITS) {
                        findings.add(ValidationFinding.error("BAD_SIGNED_INT_WIDTH", id,
                                "SIGNED_INT is limited to " + MAX_INTEGER_BITS + " bits, got " + bits));
                    }
                }
                case UNSIGNED_INT, ENUM -> {
                    if (bits > MAX_INTEGER_BITS) {
                        findings.add(ValidationFinding.error("BAD_INT_WIDTH", id,
                                p.dataType() + " is limited to " + MAX_INTEGER_BITS
                                        + " bits, got " + bits));
                    }
                }
            }
        }
    }

    /** Broken references between a parameter and its enum states. */
    private void checkEnumReferences(
            List<ParameterDefinition> parameters, List<ValidationFinding> findings) {
        for (ParameterDefinition p : parameters) {
            String id = identify(p);
            List<EnumStateDefinition> states = p.enumStatesOrEmpty();
            boolean isEnum = p.dataType() == ParameterDataType.ENUM;

            if (isEnum && states.isEmpty()) {
                findings.add(ValidationFinding.error("ENUM_WITHOUT_STATES", id,
                        "ENUM parameter defines no enumStates, so raw values cannot be labelled"));
            }
            if (!isEnum && !states.isEmpty()) {
                findings.add(ValidationFinding.error("STATES_ON_NON_ENUM", id,
                        "enumStates are defined on a " + p.dataType() + " parameter"));
            }

            Set<Long> seenValues = new HashSet<>();
            Set<String> seenLabels = new HashSet<>();
            for (EnumStateDefinition state : states) {
                if (state.rawValue() == null) {
                    findings.add(ValidationFinding.error("ENUM_STATE_MISSING_VALUE", id,
                            "an enum state has no rawValue"));
                    continue;
                }
                if (isBlank(state.label())) {
                    findings.add(ValidationFinding.error("ENUM_STATE_MISSING_LABEL", id,
                            "enum state " + state.rawValue() + " has no label"));
                }
                if (!seenValues.add(state.rawValue())) {
                    findings.add(ValidationFinding.error("DUPLICATE_ENUM_VALUE", id,
                            "rawValue " + state.rawValue() + " is defined more than once"));
                }
                if (state.label() != null && !seenLabels.add(state.label())) {
                    findings.add(ValidationFinding.warning("DUPLICATE_ENUM_LABEL", id,
                            "label \"" + state.label() + "\" is used by more than one rawValue"));
                }
                // A state the field is too narrow to ever carry is dead
                // configuration, and usually means the width is wrong.
                if (p.bitLength() != null && p.bitLength() > 0
                        && p.bitLength() <= MAX_INTEGER_BITS) {
                    long maxRepresentable = (1L << p.bitLength()) - 1;
                    if (state.rawValue() < 0 || state.rawValue() > maxRepresentable) {
                        findings.add(ValidationFinding.error("ENUM_VALUE_OUT_OF_RANGE", id,
                                "rawValue " + state.rawValue() + " does not fit in "
                                        + p.bitLength() + " bits (max " + maxRepresentable + ")"));
                    }
                }
            }
        }
    }

    private void checkCalibration(
            List<ParameterDefinition> parameters, List<ValidationFinding> findings) {
        for (ParameterDefinition p : parameters) {
            String id = identify(p);
            boolean hasCalibration =
                    (p.calScale() != null && p.calScale() != 1.0)
                            || (p.calOffset() != null && p.calOffset() != 0.0);

            if (hasCalibration && p.dataType() != null && !p.dataType().isNumeric()) {
                findings.add(ValidationFinding.warning("CALIBRATION_ON_NON_NUMERIC", id,
                        "calibration is defined on a " + p.dataType()
                                + " parameter and will be ignored"));
            }
            // Scaling everything to zero destroys the measurement; it is
            // always a typo rather than an intent.
            if (p.calScale() != null && p.calScale() == 0.0) {
                findings.add(ValidationFinding.error("ZERO_CALIBRATION_SCALE", id,
                        "calScale is zero, which would flatten every sample to the offset"));
            }
        }
    }

    /**
     * Two parameters in the same packet must not claim the same bits.
     *
     * <p>Offsets are only comparable within one APID, so parameters are grouped
     * by packet first. Within a packet they are sorted by offset and only
     * neighbours compared, which is O(n log n) rather than the O(n^2) of
     * comparing every pair.
     *
     * <p>Boundaries are exclusive: a field ending at bit 32 and one starting at
     * bit 32 are adjacent, not overlapping.
     */
    private void checkBitFieldOverlaps(
            List<ParameterDefinition> parameters, List<ValidationFinding> findings) {
        Map<Integer, List<ParameterDefinition>> byApid = new TreeMap<>();
        for (ParameterDefinition p : parameters) {
            if (p.apid() == null || p.bitOffset() == null
                    || p.bitLength() == null || p.bitLength() <= 0) {
                continue; // incomplete definitions are reported elsewhere
            }
            byApid.computeIfAbsent(p.apid(), key -> new ArrayList<>()).add(p);
        }

        for (Map.Entry<Integer, List<ParameterDefinition>> entry : byApid.entrySet()) {
            List<ParameterDefinition> packet = new ArrayList<>(entry.getValue());
            packet.sort(Comparator.comparingInt(ParameterDefinition::bitOffset));

            for (int i = 1; i < packet.size(); i++) {
                ParameterDefinition previous = packet.get(i - 1);
                ParameterDefinition current = packet.get(i);

                int previousEnd = previous.bitOffset() + previous.bitLength();
                if (current.bitOffset() < previousEnd) {
                    findings.add(ValidationFinding.error("BIT_FIELD_OVERLAP", identify(current),
                            "bits " + current.bitOffset() + "-"
                                    + (current.bitOffset() + current.bitLength() - 1)
                                    + " in apid " + entry.getKey() + " overlap "
                                    + identify(previous) + " which occupies bits "
                                    + previous.bitOffset() + "-" + (previousEnd - 1)));
                }
            }
        }
    }

    // --- helpers -------------------------------------------------------------

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** Best available identifier for error messages when the mnemonic is missing. */
    private static String identify(ParameterDefinition p) {
        if (!isBlank(p.mnemonic())) {
            return p.mnemonic();
        }
        if (!isBlank(p.name())) {
            return "(unnamed: " + p.name().toLowerCase(Locale.ROOT) + ")";
        }
        return "(unidentified parameter)";
    }
}
