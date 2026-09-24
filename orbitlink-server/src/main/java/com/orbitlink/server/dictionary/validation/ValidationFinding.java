package com.orbitlink.server.dictionary.validation;

/**
 * One problem found in a dictionary.
 *
 * @param severity  whether this blocks loading
 * @param rule      stable machine-readable rule id, e.g. BIT_FIELD_OVERLAP.
 *                  Clients and tests should branch on this rather than on
 *                  {@code message}, which is prose and free to be reworded.
 * @param mnemonic  the parameter at fault, or null for whole-file findings
 * @param message   human-readable explanation, including the offending values
 */
public record ValidationFinding(
        ValidationSeverity severity,
        String rule,
        String mnemonic,
        String message) {

    public static ValidationFinding error(String rule, String mnemonic, String message) {
        return new ValidationFinding(ValidationSeverity.ERROR, rule, mnemonic, message);
    }

    public static ValidationFinding warning(String rule, String mnemonic, String message) {
        return new ValidationFinding(ValidationSeverity.WARNING, rule, mnemonic, message);
    }
}
