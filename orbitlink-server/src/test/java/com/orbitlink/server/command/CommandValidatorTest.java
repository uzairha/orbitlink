package com.orbitlink.server.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CommandValidatorTest {

    private final CommandValidator validator = new CommandValidator();

    private static CommandDefinition commandWith(CommandArgument... arguments) {
        CommandDefinition command =
                new CommandDefinition("SET_HEATER", "Set heater state", 200, 1);
        for (CommandArgument argument : arguments) {
            command.addArgument(argument);
        }
        return command;
    }

    private static CommandArgument arg(String name, ArgumentDataType type, int position) {
        return new CommandArgument(name, type, position);
    }

    @Test
    void acceptsAValidSubmission() {
        CommandArgument duration = arg("duration", ArgumentDataType.INTEGER, 0);
        duration.setMinValue(1.0);
        duration.setMaxValue(3600.0);

        CommandValidator.Result result =
                validator.validate(commandWith(duration), Map.of("duration", 60));

        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
    }

    @Test
    void rejectsAMissingRequiredArgument() {
        CommandValidator.Result result =
                validator.validate(commandWith(arg("duration", ArgumentDataType.INTEGER, 0)), Map.of());

        assertThat(result.valid()).isFalse();
        assertThat(result.summary()).contains("missing required argument", "duration");
    }

    @Test
    void allowsAnOmittedOptionalArgument() {
        CommandArgument optional = arg("comment", ArgumentDataType.STRING, 0);
        optional.setRequired(false);

        assertThat(validator.validate(commandWith(optional), Map.of()).valid()).isTrue();
    }

    /**
     * A typo must not pass silently: "durration" would leave the real argument
     * unset while the operator believed they had set it.
     */
    @Test
    void rejectsAnUnknownArgument() {
        CommandArgument duration = arg("duration", ArgumentDataType.INTEGER, 0);

        CommandValidator.Result result = validator.validate(
                commandWith(duration), Map.of("duration", 5, "durration", 90));

        assertThat(result.valid()).isFalse();
        assertThat(result.summary()).contains("unknown argument", "durration");
    }

    @Test
    void enforcesNumericRange() {
        CommandArgument duration = arg("duration", ArgumentDataType.INTEGER, 0);
        duration.setMinValue(1.0);
        duration.setMaxValue(3600.0);

        assertThat(validator.validate(commandWith(duration), Map.of("duration", 0)).summary())
                .contains("below its minimum");
        assertThat(validator.validate(commandWith(duration), Map.of("duration", 5000)).summary())
                .contains("above its maximum");
    }

    @Test
    void acceptsValuesExactlyOnTheRangeBoundaries() {
        CommandArgument duration = arg("duration", ArgumentDataType.INTEGER, 0);
        duration.setMinValue(1.0);
        duration.setMaxValue(10.0);

        assertThat(validator.validate(commandWith(duration), Map.of("duration", 1)).valid()).isTrue();
        assertThat(validator.validate(commandWith(duration), Map.of("duration", 10)).valid()).isTrue();
    }

    /** 2.7 seconds is not 2 seconds; truncating the operator's intent is a bug. */
    @Test
    void rejectsAFractionalValueForAnIntegerArgument() {
        CommandValidator.Result result = validator.validate(
                commandWith(arg("duration", ArgumentDataType.INTEGER, 0)),
                Map.of("duration", 2.7));

        assertThat(result.valid()).isFalse();
        assertThat(result.summary()).contains("must be an integer");
    }

    @Test
    void rejectsANonNumericValueForANumericArgument() {
        CommandValidator.Result result = validator.validate(
                commandWith(arg("duration", ArgumentDataType.INTEGER, 0)),
                Map.of("duration", "soon"));

        assertThat(result.valid()).isFalse();
        assertThat(result.summary()).contains("must be an integer");
    }

    /**
     * Coercing "false" would be especially dangerous: the usual non-empty
     * string convention would turn it into true.
     */
    @Test
    void rejectsAStringForABooleanArgument() {
        CommandValidator.Result result = validator.validate(
                commandWith(arg("enable", ArgumentDataType.BOOLEAN, 0)),
                Map.of("enable", "false"));

        assertThat(result.valid()).isFalse();
        assertThat(result.summary()).contains("must be true or false");
    }

    @Test
    void acceptsARealBoolean() {
        assertThat(validator.validate(
                commandWith(arg("enable", ArgumentDataType.BOOLEAN, 0)),
                Map.of("enable", false)).valid()).isTrue();
    }

    @Test
    void enforcesEnumAllowedValues() {
        CommandArgument mode = arg("mode", ArgumentDataType.ENUM, 0);
        mode.setAllowedValues("NOMINAL,LOW_POWER,SAFE_MODE");

        assertThat(validator.validate(commandWith(mode), Map.of("mode", "SAFE_MODE")).valid())
                .isTrue();

        CommandValidator.Result bad =
                validator.validate(commandWith(mode), Map.of("mode", "TURBO"));
        assertThat(bad.valid()).isFalse();
        assertThat(bad.summary()).contains("must be one of", "TURBO");
    }

    @Test
    void rejectsAnEnumArgumentWithNoAllowedValuesDefined() {
        CommandValidator.Result result = validator.validate(
                commandWith(arg("mode", ArgumentDataType.ENUM, 0)), Map.of("mode", "ANYTHING"));

        assertThat(result.valid()).isFalse();
        assertThat(result.summary()).contains("no allowed values defined");
    }

    @Test
    void rejectsANestedStructureForAStringArgument() {
        CommandValidator.Result result = validator.validate(
                commandWith(arg("comment", ArgumentDataType.STRING, 0)),
                Map.of("comment", List.of("a", "b")));

        assertThat(result.valid()).isFalse();
        assertThat(result.summary()).contains("must be a simple value");
    }

    /** Every problem is reported at once, not one per submission. */
    @Test
    void reportsEveryProblemInOnePass() {
        CommandArgument duration = arg("duration", ArgumentDataType.INTEGER, 0);
        duration.setMinValue(1.0);
        CommandArgument mode = arg("mode", ArgumentDataType.ENUM, 1);
        mode.setAllowedValues("A,B");

        CommandValidator.Result result = validator.validate(
                commandWith(duration, mode),
                Map.of("duration", -5, "mode", "Z", "bogus", 1));

        assertThat(result.errors()).hasSize(3);
    }

    @Test
    void treatsAnAbsentArgumentMapAsEmpty() {
        CommandValidator.Result result =
                validator.validate(commandWith(arg("duration", ArgumentDataType.INTEGER, 0)), null);

        assertThat(result.valid()).isFalse();
        assertThat(result.summary()).contains("missing required argument");
    }

    @Test
    void acceptsACommandWithNoArguments() {
        assertThat(validator.validate(commandWith(), Map.of()).valid()).isTrue();
    }
}
