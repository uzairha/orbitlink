package com.orbitlink.server.alarm;

/**
 * How badly a parameter is out of limits.
 *
 * <p>Ordered least to most severe so {@link #compareTo} expresses escalation
 * directly. OK is a member rather than a null, which keeps the limit checker
 * total: every sample gets a severity, and callers never branch on null.
 */
public enum AlarmSeverity {
    OK,
    WARNING,
    CRITICAL;

    public boolean isViolation() {
        return this != OK;
    }
}
