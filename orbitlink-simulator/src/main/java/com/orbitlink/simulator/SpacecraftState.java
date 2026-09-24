package com.orbitlink.simulator;

import java.util.random.RandomGenerator;

/**
 * A crude but plausible spacecraft physics model.
 *
 * <p>Values evolve continuously instead of being drawn independently each tick.
 * That matters for what comes later: limit checking (phase 6) is about a value
 * <em>crossing</em> a threshold, and uncorrelated noise would trip alarms
 * constantly while never producing a realistic excursion. Here the state walks,
 * so a fault produces a believable trend.
 *
 * <p>The orbit drives everything. A ~90 minute low Earth orbit is compressed to
 * {@link #ORBIT_PERIOD_SECONDS} so a demo shows several day/night cycles in a
 * couple of minutes: in eclipse the array stops producing, the battery
 * discharges and cools; in sunlight it charges and warms.
 */
public final class SpacecraftState {

    /** Compressed orbit period so a demo is watchable. */
    private static final double ORBIT_PERIOD_SECONDS = 120.0;

    /** Fraction of each orbit spent in Earth's shadow. */
    private static final double ECLIPSE_FRACTION = 0.35;

    public enum EpsMode {
        NOMINAL(0), LOW_POWER(1), SAFE_MODE(2), SURVIVAL(3);

        private final int rawValue;

        EpsMode(int rawValue) {
            this.rawValue = rawValue;
        }

        public int rawValue() {
            return rawValue;
        }
    }

    public enum GyroHealth {
        OK(0), DEGRADED(1), FAILED(2);

        private final int rawValue;

        GyroHealth(int rawValue) {
            this.rawValue = rawValue;
        }

        public int rawValue() {
            return rawValue;
        }
    }

    private final RandomGenerator random;

    private double elapsedSeconds;

    // Engineering units, matching the telemetry dictionary.
    private double batteryVoltage = 28.0;   // V
    private double batteryCurrent = 0.0;    // A, positive on discharge
    private double batteryTemp = 18.0;      // degC
    private double solarArrayPower = 0.0;   // W
    private double roll = 0.0;              // deg
    private double pitch = 0.0;             // deg
    private double yaw = 0.0;               // deg
    private EpsMode epsMode = EpsMode.NOMINAL;
    private GyroHealth gyroHealth = GyroHealth.OK;
    private boolean heaterOn;

    public SpacecraftState(RandomGenerator random) {
        this.random = random;
    }

    /** Advances the model by {@code deltaSeconds}. */
    public void advance(double deltaSeconds) {
        elapsedSeconds += deltaSeconds;

        double orbitPhase = (elapsedSeconds % ORBIT_PERIOD_SECONDS) / ORBIT_PERIOD_SECONDS;
        boolean inSunlight = orbitPhase > ECLIPSE_FRACTION;

        if (inSunlight) {
            // Illumination follows the sun angle across the sunlit arc, so
            // power ramps up and back down rather than switching on square.
            double sunAngle = (orbitPhase - ECLIPSE_FRACTION) / (1.0 - ECLIPSE_FRACTION);
            solarArrayPower = 380.0 * Math.sin(Math.PI * sunAngle) + noise(4.0);
            solarArrayPower = Math.max(0.0, solarArrayPower);
        } else {
            solarArrayPower = noise(0.5);
            solarArrayPower = Math.max(0.0, solarArrayPower);
        }

        // Load is roughly constant; the array either covers it or the battery
        // makes up the difference.
        double loadWatts = 120.0 + noise(5.0);
        double netWatts = solarArrayPower - loadWatts;
        batteryCurrent = -netWatts / Math.max(batteryVoltage, 1.0);

        // Charging raises the bus, discharging sags it, bounded either side.
        batteryVoltage += (-batteryCurrent * 0.002) + noise(0.01);
        batteryVoltage = clamp(batteryVoltage, 24.0, 32.5);

        // Thermal inertia: the pack drifts toward its environment slowly.
        double targetTemp = inSunlight ? 26.0 : 8.0;
        batteryTemp += (targetTemp - batteryTemp) * 0.02 + noise(0.05);

        // Heater has hysteresis so it does not chatter around the setpoint.
        if (batteryTemp < 5.0) {
            heaterOn = true;
        } else if (batteryTemp > 10.0) {
            heaterOn = false;
        }

        // A slow tumble, wrapped into the dictionary's declared ranges.
        roll = wrap(roll + 0.9 * deltaSeconds + noise(0.05), -180.0, 180.0);
        pitch = clamp(pitch + 0.15 * Math.sin(elapsedSeconds / 20.0) + noise(0.05), -90.0, 90.0);
        yaw = wrap(yaw + 0.35 * deltaSeconds + noise(0.05), -180.0, 180.0);

        epsMode = batteryVoltage < 25.5
                ? EpsMode.LOW_POWER
                : EpsMode.NOMINAL;
    }

    /**
     * Forces a parameter outside its declared limits so the ground system's
     * limit checking has something to catch.
     *
     * <p>Faults are injected into the model rather than into the encoded
     * packet. A value that is merely scribbled over on the wire would snap
     * back to normal on the very next sample; changing the state means the
     * excursion persists and then recovers, which is what a real anomaly looks
     * like and what makes an alarm worth raising.
     */
    public void injectFault(RandomGenerator source) {
        switch (source.nextInt(4)) {
            case 0 -> batteryVoltage = 35.0 + source.nextDouble() * 2.0;  // over 34.0 V max
            case 1 -> batteryVoltage = 20.0 - source.nextDouble() * 2.0;  // under 22.0 V min
            case 2 -> batteryTemp = 65.0 + source.nextDouble() * 5.0;     // over 60 degC max
            default -> {
                batteryTemp = -25.0 - source.nextDouble() * 5.0;          // under -20 degC min
                gyroHealth = GyroHealth.DEGRADED;
            }
        }
    }

    private double noise(double magnitude) {
        return (random.nextDouble() - 0.5) * 2.0 * magnitude;
    }

    private static double clamp(double value, double min, double max) {
        return Math.min(max, Math.max(min, value));
    }

    private static double wrap(double value, double min, double max) {
        double span = max - min;
        double shifted = value - min;
        return min + (shifted - Math.floor(shifted / span) * span);
    }

    public double batteryVoltage() {
        return batteryVoltage;
    }

    public double batteryCurrent() {
        return batteryCurrent;
    }

    public double batteryTemp() {
        return batteryTemp;
    }

    public double solarArrayPower() {
        return solarArrayPower;
    }

    public double roll() {
        return roll;
    }

    public double pitch() {
        return pitch;
    }

    public double yaw() {
        return yaw;
    }

    public EpsMode epsMode() {
        return epsMode;
    }

    public GyroHealth gyroHealth() {
        return gyroHealth;
    }

    public boolean heaterOn() {
        return heaterOn;
    }
}
