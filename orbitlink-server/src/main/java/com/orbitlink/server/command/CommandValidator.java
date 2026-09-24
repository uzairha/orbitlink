package com.orbitlink.server.command;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Validates submitted arguments against a command definition.
 *
 * <p>Collects every problem rather than failing on the first, for the same
 * reason the dictionary validator does: an operator fixing a command wants the
 * whole list, not one error at a time.
 *
 * <p>Rejects unknown arguments rather than ignoring them. A typo like
 * {@code durration} would otherwise leave the real {@code duration} at its
 * default while the operator believed they had set it — silently sending a
 * different command than intended, which is exactly the failure mode command
 * validation exists to prevent.
 */
@Service
public class CommandValidator {

    /** @param errors empty when the command may be sent */
    public record Result(boolean valid, List<String> errors) {

        static Result ok() {
            return new Result(true, List.of());
        }

        static Result failed(List<String> errors) {
            return new Result(false, List.copyOf(errors));
        }

        public String summary() {
            return String.join("; ", errors);
        }
    }

    public Result validate(CommandDefinition command, Map<String, Object> submitted) {
        List<String> errors = new ArrayList<>();
        Map<String, Object> arguments = submitted == null ? Map.of() : submitted;

        Set<String> defined = new HashSet<>();
        for (CommandArgument argument : command.getArguments()) {
            defined.add(argument.getName());
            validateOne(argument, arguments, errors);
        }

        for (String name : arguments.keySet()) {
            if (!defined.contains(name)) {
                errors.add("unknown argument \"" + name + "\"");
            }
        }

        return errors.isEmpty() ? Result.ok() : Result.failed(errors);
    }

    private void validateOne(
            CommandArgument argument, Map<String, Object> arguments, List<String> errors) {

        String name = argument.getName();
        boolean present = arguments.containsKey(name) && arguments.get(name) != null;

        if (!present) {
            if (argument.isRequired()) {
                errors.add("missing required argument \"" + name + "\"");
            }
            return;
        }

        Object raw = arguments.get(name);

        switch (argument.getDataType()) {
            case INTEGER -> validateInteger(argument, raw, errors);
            case FLOAT -> validateFloat(argument, raw, errors);
            case BOOLEAN -> validateBoolean(argument, raw, errors);
            case ENUM -> validateEnum(argument, raw, errors);
            case STRING -> {
                // Any scalar is acceptable as a string; a nested structure is
                // not, because it cannot be packed into an uplink field.
                if (raw instanceof Map || raw instanceof List) {
                    errors.add("argument \"" + name + "\" must be a simple value");
                }
            }
        }
    }

    private void validateInteger(CommandArgument argument, Object raw, List<String> errors) {
        String name = argument.getName();
        Long value = asLong(raw);
        if (value == null) {
            errors.add("argument \"" + name + "\" must be an integer, got " + describe(raw));
            return;
        }
        checkRange(argument, value.doubleValue(), errors);
    }

    private void validateFloat(CommandArgument argument, Object raw, List<String> errors) {
        String name = argument.getName();
        Double value = asDouble(raw);
        if (value == null) {
            errors.add("argument \"" + name + "\" must be a number, got " + describe(raw));
            return;
        }
        if (value.isNaN() || value.isInfinite()) {
            errors.add("argument \"" + name + "\" must be a finite number");
            return;
        }
        checkRange(argument, value, errors);
    }

    /**
     * Only a real boolean is accepted. Coercing the string "false" would be
     * especially dangerous here: the common convention that a non-empty string
     * is truthy would turn an operator's "false" into true.
     */
    private void validateBoolean(CommandArgument argument, Object raw, List<String> errors) {
        if (!(raw instanceof Boolean)) {
            errors.add("argument \"" + argument.getName()
                    + "\" must be true or false, got " + describe(raw));
        }
    }

    private void validateEnum(CommandArgument argument, Object raw, List<String> errors) {
        List<String> allowed = argument.allowedValueList();
        if (allowed.isEmpty()) {
            errors.add("argument \"" + argument.getName()
                    + "\" is an enum with no allowed values defined");
            return;
        }
        String value = String.valueOf(raw);
        if (!allowed.contains(value)) {
            errors.add("argument \"" + argument.getName() + "\" must be one of "
                    + String.join(", ", allowed) + ", got \"" + value + "\"");
        }
    }

    private void checkRange(CommandArgument argument, double value, List<String> errors) {
        String name = argument.getName();
        if (argument.getMinValue() != null && value < argument.getMinValue()) {
            errors.add("argument \"" + name + "\" is below its minimum of "
                    + argument.getMinValue() + ", got " + value);
        }
        if (argument.getMaxValue() != null && value > argument.getMaxValue()) {
            errors.add("argument \"" + name + "\" is above its maximum of "
                    + argument.getMaxValue() + ", got " + value);
        }
    }

    /**
     * A floating-point value is not silently truncated to an integer: an
     * operator who typed 2.7 seconds did not mean 2, and rounding their intent
     * away without telling them is how a command does the wrong thing.
     */
    private static Long asLong(Object raw) {
        if (raw instanceof Integer i) {
            return i.longValue();
        }
        if (raw instanceof Long l) {
            return l;
        }
        if (raw instanceof Number n && n.doubleValue() == Math.floor(n.doubleValue())
                && !Double.isInfinite(n.doubleValue())) {
            return n.longValue();
        }
        if (raw instanceof String s) {
            try {
                return Long.parseLong(s.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static Double asDouble(Object raw) {
        if (raw instanceof Number n) {
            return n.doubleValue();
        }
        if (raw instanceof String s) {
            try {
                return Double.parseDouble(s.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static String describe(Object raw) {
        return raw == null ? "null" : "\"" + raw + "\"";
    }
}
