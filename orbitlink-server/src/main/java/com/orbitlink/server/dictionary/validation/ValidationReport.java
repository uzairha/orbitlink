package com.orbitlink.server.dictionary.validation;

import java.util.List;

/**
 * The result of validating one dictionary.
 *
 * <p>Reports every finding rather than stopping at the first. Someone fixing a
 * dictionary wants the whole list in one pass, not a fix-recompile-discover
 * loop — the same reason a compiler does not stop at the first syntax error.
 *
 * @param valid        true when there are no ERROR findings; warnings alone
 *                     still allow the dictionary to load
 * @param errorCount   number of ERROR findings
 * @param warningCount number of WARNING findings
 * @param findings     every finding, errors first
 */
public record ValidationReport(
        String dictionaryVersion,
        boolean valid,
        int errorCount,
        int warningCount,
        List<ValidationFinding> findings) {

    public static ValidationReport of(String dictionaryVersion, List<ValidationFinding> findings) {
        List<ValidationFinding> ordered = findings.stream()
                .sorted((a, b) -> {
                    int bySeverity = a.severity().compareTo(b.severity());
                    return bySeverity != 0 ? bySeverity : a.rule().compareTo(b.rule());
                })
                .toList();

        int errors = (int) ordered.stream()
                .filter(f -> f.severity() == ValidationSeverity.ERROR)
                .count();

        return new ValidationReport(
                dictionaryVersion,
                errors == 0,
                errors,
                ordered.size() - errors,
                ordered);
    }
}
