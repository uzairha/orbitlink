package com.orbitlink.server.alarm;

import static org.assertj.core.api.Assertions.assertThat;

import com.orbitlink.server.dictionary.ParameterDataType;
import com.orbitlink.server.dictionary.TelemetryParameter;
import org.junit.jupiter.api.Test;

class LimitCheckerTest {

    /** Battery temperature: critical -20/60, warning -10/45. */
    private static TelemetryParameter battTemp() {
        TelemetryParameter p = new TelemetryParameter(
                "BATT_TEMP", "Battery temperature", 100,
                ParameterDataType.SIGNED_INT, 32, 16);
        p.setMinValue(-20.0);
        p.setMaxValue(60.0);
        p.setWarnLow(-10.0);
        p.setWarnHigh(45.0);
        return p;
    }

    @Test
    void reportsOkInsideEveryLimit() {
        assertThat(LimitChecker.check(battTemp(), 20.0).severity())
                .isEqualTo(AlarmSeverity.OK);
    }

    @Test
    void reportsWarningAboveTheWarningHigh() {
        LimitCheck check = LimitChecker.check(battTemp(), 50.0);

        assertThat(check.severity()).isEqualTo(AlarmSeverity.WARNING);
        assertThat(check.violatedLimit()).isEqualTo(LimitCheck.ViolatedLimit.WARNING_HIGH);
        assertThat(check.limitValue()).isEqualTo(45.0);
    }

    @Test
    void reportsWarningBelowTheWarningLow() {
        LimitCheck check = LimitChecker.check(battTemp(), -15.0);

        assertThat(check.severity()).isEqualTo(AlarmSeverity.WARNING);
        assertThat(check.violatedLimit()).isEqualTo(LimitCheck.ViolatedLimit.WARNING_LOW);
    }

    @Test
    void reportsCriticalAboveTheCriticalHigh() {
        LimitCheck check = LimitChecker.check(battTemp(), 70.0);

        assertThat(check.severity()).isEqualTo(AlarmSeverity.CRITICAL);
        assertThat(check.violatedLimit()).isEqualTo(LimitCheck.ViolatedLimit.CRITICAL_HIGH);
        assertThat(check.limitValue()).isEqualTo(60.0);
    }

    @Test
    void reportsCriticalBelowTheCriticalLow() {
        LimitCheck check = LimitChecker.check(battTemp(), -29.01);

        assertThat(check.severity()).isEqualTo(AlarmSeverity.CRITICAL);
        assertThat(check.violatedLimit()).isEqualTo(LimitCheck.ViolatedLimit.CRITICAL_LOW);
    }

    /**
     * The bands are nested, so a critically low value is also below the
     * warning low. Only the worse of the two should be reported.
     */
    @Test
    void reportsOnlyTheWorseSeverityWhenBothBandsAreBroken() {
        assertThat(LimitChecker.check(battTemp(), -100.0).severity())
                .isEqualTo(AlarmSeverity.CRITICAL);
        assertThat(LimitChecker.check(battTemp(), 100.0).violatedLimit())
                .isEqualTo(LimitCheck.ViolatedLimit.CRITICAL_HIGH);
    }

    // --- boundaries ----------------------------------------------------------

    /** Comparisons are strict, so a value exactly on a limit is not in alarm. */
    @Test
    void treatsAValueExactlyOnTheWarningLimitAsOk() {
        assertThat(LimitChecker.check(battTemp(), 45.0).severity()).isEqualTo(AlarmSeverity.OK);
        assertThat(LimitChecker.check(battTemp(), -10.0).severity()).isEqualTo(AlarmSeverity.OK);
    }

    @Test
    void treatsAValueExactlyOnTheCriticalLimitAsWarningNotCritical() {
        // 60.0 is not > 60.0, so it is not critical; it is > 45.0, so warning.
        assertThat(LimitChecker.check(battTemp(), 60.0).severity())
                .isEqualTo(AlarmSeverity.WARNING);
    }

    @Test
    void detectsTheSmallestExcursionPastALimit() {
        assertThat(LimitChecker.check(battTemp(), 45.0001).severity())
                .isEqualTo(AlarmSeverity.WARNING);
        assertThat(LimitChecker.check(battTemp(), 60.0001).severity())
                .isEqualTo(AlarmSeverity.CRITICAL);
    }

    // --- partial and absent limits -------------------------------------------

    @Test
    void ignoresSidesWithNoLimitDefined() {
        TelemetryParameter p = new TelemetryParameter(
                "P", "P", 100, ParameterDataType.UNSIGNED_INT, 0, 16);
        p.setMaxValue(100.0);

        assertThat(LimitChecker.check(p, -1_000_000.0).severity()).isEqualTo(AlarmSeverity.OK);
        assertThat(LimitChecker.check(p, 101.0).severity()).isEqualTo(AlarmSeverity.CRITICAL);
    }

    @Test
    void reportsOkWhenNoLimitsAreDefinedAtAll() {
        TelemetryParameter p = new TelemetryParameter(
                "P", "P", 100, ParameterDataType.UNSIGNED_INT, 0, 16);

        assertThat(LimitChecker.check(p, 999_999.0).severity()).isEqualTo(AlarmSeverity.OK);
    }

    /** A zero limit is a real limit, not an absent one. */
    @Test
    void honoursAZeroLimit() {
        TelemetryParameter p = new TelemetryParameter(
                "P", "P", 100, ParameterDataType.SIGNED_INT, 0, 16);
        p.setMinValue(0.0);

        assertThat(LimitChecker.check(p, -0.5).severity()).isEqualTo(AlarmSeverity.CRITICAL);
        assertThat(LimitChecker.check(p, 0.0).severity()).isEqualTo(AlarmSeverity.OK);
    }

    // --- non-numeric and NaN --------------------------------------------------

    /** There is no ordering over enum labels, so limits cannot apply. */
    @Test
    void neverAlarmsOnAnEnumOrBoolean() {
        TelemetryParameter mode = new TelemetryParameter(
                "MODE", "Mode", 100, ParameterDataType.ENUM, 0, 8);
        mode.setMaxValue(1.0);

        TelemetryParameter flag = new TelemetryParameter(
                "FLAG", "Flag", 100, ParameterDataType.BOOLEAN, 0, 1);
        flag.setMaxValue(0.0);

        assertThat(LimitChecker.check(mode, 3.0).severity()).isEqualTo(AlarmSeverity.OK);
        assertThat(LimitChecker.check(flag, 1.0).severity()).isEqualTo(AlarmSeverity.OK);
    }

    /**
     * NaN compares false against every bound, so without an explicit check it
     * would slip through as healthy. A value that is not a number is not a
     * good reading.
     */
    @Test
    void treatsNaNAsCritical() {
        assertThat(LimitChecker.check(battTemp(), Double.NaN).severity())
                .isEqualTo(AlarmSeverity.CRITICAL);
    }

    @Test
    void treatsInfinityAsCritical() {
        assertThat(LimitChecker.check(battTemp(), Double.POSITIVE_INFINITY).severity())
                .isEqualTo(AlarmSeverity.CRITICAL);
        assertThat(LimitChecker.check(battTemp(), Double.NEGATIVE_INFINITY).severity())
                .isEqualTo(AlarmSeverity.CRITICAL);
    }
}
