package com.orbitlink.server.dictionary.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.orbitlink.server.dictionary.ParameterDataType;
import com.orbitlink.server.dictionary.yaml.DictionaryFile;
import com.orbitlink.server.dictionary.yaml.EnumStateDefinition;
import com.orbitlink.server.dictionary.yaml.ParameterDefinition;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * One test per rule, asserting on the rule id rather than the message text so
 * that rewording a message never breaks a test.
 */
class DictionaryValidatorTest {

    private final DictionaryValidator validator = new DictionaryValidator();

    // --- builders ------------------------------------------------------------

    private static ParameterDefinition param(
            String mnemonic, int apid, ParameterDataType type, int offset, int length) {
        return new ParameterDefinition(
                mnemonic, mnemonic + " name", null, apid, type, offset, length,
                null, null, null, null, null, null);
    }

    private static DictionaryFile file(ParameterDefinition... parameters) {
        return new DictionaryFile("1.0.0", "test", List.of(parameters));
    }

    private static List<String> rules(ValidationReport report) {
        return report.findings().stream().map(ValidationFinding::rule).toList();
    }

    // --- happy path ----------------------------------------------------------

    @Test
    void acceptsAWellFormedDictionary() {
        ValidationReport report = validator.validate(file(
                param("BATT_V", 100, ParameterDataType.UNSIGNED_INT, 0, 16),
                param("BATT_I", 100, ParameterDataType.SIGNED_INT, 16, 16)));

        assertThat(report.valid()).isTrue();
        assertThat(report.errorCount()).isZero();
        assertThat(report.findings()).isEmpty();
    }

    // --- duplicates ----------------------------------------------------------

    @Test
    void flagsDuplicateMnemonicsAsErrors() {
        ValidationReport report = validator.validate(file(
                param("BATT_V", 100, ParameterDataType.UNSIGNED_INT, 0, 16),
                param("BATT_V", 101, ParameterDataType.UNSIGNED_INT, 0, 16)));

        assertThat(rules(report)).contains("DUPLICATE_MNEMONIC");
        assertThat(report.valid()).isFalse();
    }

    /** Duplicate display names are confusing, not broken, so only a warning. */
    @Test
    void flagsDuplicateDisplayNamesAsWarningsOnly() {
        ParameterDefinition a = new ParameterDefinition(
                "A", "Bus voltage", null, 100, ParameterDataType.UNSIGNED_INT, 0, 16,
                null, null, null, null, null, null);
        ParameterDefinition b = new ParameterDefinition(
                "B", "Bus voltage", null, 100, ParameterDataType.UNSIGNED_INT, 16, 16,
                null, null, null, null, null, null);

        ValidationReport report = validator.validate(file(a, b));

        assertThat(rules(report)).contains("DUPLICATE_NAME");
        assertThat(report.valid()).isTrue();
        assertThat(report.warningCount()).isEqualTo(1);
    }

    // --- limits --------------------------------------------------------------

    @Test
    void flagsInvertedLimits() {
        ParameterDefinition p = new ParameterDefinition(
                "TEMP", "Temp", null, 100, ParameterDataType.SIGNED_INT, 0, 16,
                "degC", 80.0, -20.0, null, null, null);

        assertThat(rules(validator.validate(file(p)))).contains("INVERTED_LIMITS");
    }

    @Test
    void acceptsEqualMinAndMax() {
        ParameterDefinition p = new ParameterDefinition(
                "FIXED", "Fixed", null, 100, ParameterDataType.UNSIGNED_INT, 0, 16,
                "count", 5.0, 5.0, null, null, null);

        assertThat(validator.validate(file(p)).valid()).isTrue();
    }

    // --- units ---------------------------------------------------------------

    @Test
    void warnsOnUnknownUnitsWithoutBlockingTheLoad() {
        ParameterDefinition p = new ParameterDefinition(
                "SENSOR", "Sensor", null, 100, ParameterDataType.UNSIGNED_INT, 0, 16,
                "furlongs", null, null, null, null, null);

        ValidationReport report = validator.validate(file(p));

        assertThat(rules(report)).contains("UNKNOWN_UNIT");
        assertThat(report.valid()).isTrue();
    }

    @Test
    void acceptsKnownUnits() {
        ParameterDefinition p = new ParameterDefinition(
                "BATT_V", "Bus voltage", null, 100, ParameterDataType.UNSIGNED_INT, 0, 16,
                "V", null, null, null, null, null);

        assertThat(validator.validate(file(p)).findings()).isEmpty();
    }

    // --- bit field widths ----------------------------------------------------

    @Test
    void rejectsABooleanWiderThanOneBit() {
        assertThat(rules(validator.validate(file(
                param("FLAG", 100, ParameterDataType.BOOLEAN, 0, 8)))))
                .contains("BAD_BOOLEAN_WIDTH");
    }

    @Test
    void rejectsAFloatThatIsNot32Or64Bits() {
        assertThat(rules(validator.validate(file(
                param("F", 100, ParameterDataType.FLOAT, 0, 16)))))
                .contains("BAD_FLOAT_WIDTH");
    }

    /** One bit of two's complement can only mean 0 or -1. */
    @Test
    void rejectsASingleBitSignedInteger() {
        assertThat(rules(validator.validate(file(
                param("S", 100, ParameterDataType.SIGNED_INT, 0, 1)))))
                .contains("BAD_SIGNED_INT_WIDTH");
    }

    @Test
    void rejectsIntegerFieldsWiderThan32Bits() {
        assertThat(rules(validator.validate(file(
                param("BIG", 100, ParameterDataType.UNSIGNED_INT, 0, 40)))))
                .contains("BAD_INT_WIDTH");
    }

    // --- enum references -----------------------------------------------------

    @Test
    void rejectsAnEnumWithNoStates() {
        assertThat(rules(validator.validate(file(
                param("MODE", 100, ParameterDataType.ENUM, 0, 8)))))
                .contains("ENUM_WITHOUT_STATES");
    }

    @Test
    void rejectsEnumStatesOnANonEnumParameter() {
        ParameterDefinition p = new ParameterDefinition(
                "V", "V", null, 100, ParameterDataType.UNSIGNED_INT, 0, 16,
                null, null, null, null, null,
                List.of(new EnumStateDefinition(0L, "OFF")));

        assertThat(rules(validator.validate(file(p)))).contains("STATES_ON_NON_ENUM");
    }

    @Test
    void rejectsDuplicateEnumRawValues() {
        ParameterDefinition p = new ParameterDefinition(
                "MODE", "Mode", null, 100, ParameterDataType.ENUM, 0, 8,
                null, null, null, null, null,
                List.of(new EnumStateDefinition(1L, "A"), new EnumStateDefinition(1L, "B")));

        assertThat(rules(validator.validate(file(p)))).contains("DUPLICATE_ENUM_VALUE");
    }

    /** A state the field is too narrow to carry is dead configuration. */
    @Test
    void rejectsAnEnumValueTooLargeForItsField() {
        ParameterDefinition p = new ParameterDefinition(
                "MODE", "Mode", null, 100, ParameterDataType.ENUM, 0, 2,
                null, null, null, null, null,
                List.of(new EnumStateDefinition(0L, "A"), new EnumStateDefinition(9L, "B")));

        assertThat(rules(validator.validate(file(p)))).contains("ENUM_VALUE_OUT_OF_RANGE");
    }

    // --- calibration ---------------------------------------------------------

    @Test
    void rejectsAZeroCalibrationScale() {
        ParameterDefinition p = new ParameterDefinition(
                "V", "V", null, 100, ParameterDataType.UNSIGNED_INT, 0, 16,
                "V", null, null, 0.0, null, null);

        assertThat(rules(validator.validate(file(p)))).contains("ZERO_CALIBRATION_SCALE");
    }

    // --- bit field overlaps --------------------------------------------------

    @Test
    void detectsOverlappingBitFieldsInTheSamePacket() {
        ValidationReport report = validator.validate(file(
                param("A", 100, ParameterDataType.UNSIGNED_INT, 0, 16),
                param("B", 100, ParameterDataType.UNSIGNED_INT, 8, 16)));

        assertThat(rules(report)).contains("BIT_FIELD_OVERLAP");
        assertThat(report.valid()).isFalse();
    }

    /** Boundaries are exclusive, so touching fields are neighbours. */
    @Test
    void treatsAdjacentBitFieldsAsValid() {
        ValidationReport report = validator.validate(file(
                param("A", 100, ParameterDataType.UNSIGNED_INT, 0, 16),
                param("B", 100, ParameterDataType.UNSIGNED_INT, 16, 16)));

        assertThat(report.findings()).isEmpty();
    }

    /** Offsets are only comparable within a packet, so identical offsets in
     *  different APIDs are perfectly normal. */
    @Test
    void doesNotCompareBitFieldsAcrossDifferentApids() {
        ValidationReport report = validator.validate(file(
                param("A", 100, ParameterDataType.UNSIGNED_INT, 0, 16),
                param("B", 101, ParameterDataType.UNSIGNED_INT, 0, 16)));

        assertThat(report.findings()).isEmpty();
    }

    @Test
    void detectsOverlapEvenWhenParametersAreDeclaredOutOfOrder() {
        ValidationReport report = validator.validate(file(
                param("LATER", 100, ParameterDataType.UNSIGNED_INT, 20, 16),
                param("EARLIER", 100, ParameterDataType.UNSIGNED_INT, 0, 24)));

        assertThat(rules(report)).contains("BIT_FIELD_OVERLAP");
    }

    // --- missing fields and reporting ----------------------------------------

    @Test
    void reportsMissingRequiredFields() {
        ParameterDefinition p = new ParameterDefinition(
                null, null, null, null, null, null, null,
                null, null, null, null, null, null);

        assertThat(rules(validator.validate(file(p)))).contains(
                "MISSING_MNEMONIC", "MISSING_NAME", "MISSING_APID",
                "MISSING_DATA_TYPE", "MISSING_BIT_OFFSET", "MISSING_BIT_LENGTH");
    }

    /** Every problem is reported in one pass, not just the first. */
    @Test
    void reportsAllFindingsAtOnceWithErrorsFirst() {
        ParameterDefinition bad = new ParameterDefinition(
                "BAD", "Bad", null, 100, ParameterDataType.UNSIGNED_INT, 0, 16,
                "furlongs", 10.0, 1.0, null, null, null);

        ValidationReport report = validator.validate(file(bad));

        assertThat(report.errorCount()).isPositive();
        assertThat(report.warningCount()).isPositive();
        assertThat(report.findings().get(0).severity()).isEqualTo(ValidationSeverity.ERROR);
        assertThat(report.findings().get(report.findings().size() - 1).severity())
                .isEqualTo(ValidationSeverity.WARNING);
    }
}
