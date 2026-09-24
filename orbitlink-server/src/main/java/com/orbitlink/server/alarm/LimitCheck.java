package com.orbitlink.server.alarm;

/**
 * The outcome of checking one value against one parameter's limits.
 *
 * @param severity      OK, WARNING or CRITICAL
 * @param violatedLimit which threshold was crossed, or null when OK
 * @param limitValue    the threshold's value, or 0 when OK
 */
public record LimitCheck(AlarmSeverity severity, ViolatedLimit violatedLimit, double limitValue) {

    /** Which side of which band was crossed. */
    public enum ViolatedLimit {
        CRITICAL_LOW,
        WARNING_LOW,
        WARNING_HIGH,
        CRITICAL_HIGH
    }

    public static final LimitCheck OK = new LimitCheck(AlarmSeverity.OK, null, 0.0);

    public boolean isViolation() {
        return severity.isViolation();
    }
}
