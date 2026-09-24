package com.orbitlink.server.dictionary.validation;

/**
 * How serious a validation finding is.
 *
 * <p>The distinction is load-blocking versus advisory. An ERROR means the
 * dictionary cannot be decoded correctly, so loading it is refused. A WARNING
 * means something looks wrong but has a legitimate explanation often enough
 * that refusing would be worse than allowing — an unrecognised unit on a
 * mission-specific sensor, for instance.
 */
public enum ValidationSeverity {
    ERROR,
    WARNING
}
