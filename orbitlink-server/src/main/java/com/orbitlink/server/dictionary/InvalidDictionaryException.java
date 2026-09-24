package com.orbitlink.server.dictionary;

import com.orbitlink.server.dictionary.validation.ValidationReport;

/**
 * Thrown when a dictionary with ERROR-level findings is submitted for loading.
 *
 * <p>Carries the whole report rather than just a message, so the REST layer can
 * hand the caller every problem at once instead of only the first.
 */
public class InvalidDictionaryException extends RuntimeException {

    private final transient ValidationReport report;

    public InvalidDictionaryException(ValidationReport report) {
        super("dictionary " + report.dictionaryVersion() + " has "
                + report.errorCount() + " validation error(s)");
        this.report = report;
    }

    public ValidationReport getReport() {
        return report;
    }
}
