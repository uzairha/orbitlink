package com.orbitlink.server.alarm;

import com.orbitlink.server.dictionary.TelemetryParameter;

/**
 * Classifies one engineering value against a parameter's limits.
 *
 * <p>Pure and static, for the same reason the packet decoder is: this is the
 * arithmetic most likely to be subtly wrong at a boundary, and it should be
 * testable without a database or a running pipeline.
 *
 * <p>Critical is checked before warning. The bands are nested, so a value below
 * the critical low is also below the warning low — reporting only the worse of
 * the two is what an operator needs.
 */
public final class LimitChecker {

    private LimitChecker() {
        // Static utility.
    }

    public static LimitCheck check(TelemetryParameter parameter, double value) {
        // Limits are meaningless for an enum label or a flag: there is no
        // ordering over "SAFE_MODE" and "NOMINAL".
        if (!parameter.getDataType().isNumeric()) {
            return LimitCheck.OK;
        }
        // NaN compares false against every bound, so it would slip through as
        // OK. A value that is not a number is not a healthy reading.
        if (Double.isNaN(value)) {
            return new LimitCheck(AlarmSeverity.CRITICAL, LimitCheck.ViolatedLimit.CRITICAL_HIGH, Double.NaN);
        }

        Double criticalLow = parameter.getMinValue();
        Double criticalHigh = parameter.getMaxValue();
        Double warningLow = parameter.getWarnLow();
        Double warningHigh = parameter.getWarnHigh();

        if (criticalLow != null && value < criticalLow) {
            return new LimitCheck(AlarmSeverity.CRITICAL, LimitCheck.ViolatedLimit.CRITICAL_LOW, criticalLow);
        }
        if (criticalHigh != null && value > criticalHigh) {
            return new LimitCheck(AlarmSeverity.CRITICAL, LimitCheck.ViolatedLimit.CRITICAL_HIGH, criticalHigh);
        }
        if (warningLow != null && value < warningLow) {
            return new LimitCheck(AlarmSeverity.WARNING, LimitCheck.ViolatedLimit.WARNING_LOW, warningLow);
        }
        if (warningHigh != null && value > warningHigh) {
            return new LimitCheck(AlarmSeverity.WARNING, LimitCheck.ViolatedLimit.WARNING_HIGH, warningHigh);
        }
        return LimitCheck.OK;
    }
}
