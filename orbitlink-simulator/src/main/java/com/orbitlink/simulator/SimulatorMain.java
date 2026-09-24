package com.orbitlink.simulator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point for the spacecraft simulator.
 *
 * <p>Phase 4 fills this in: generate CCSDS space packets with realistic
 * telemetry values and stream them over TCP to the server's ingestion
 * listener, periodically injecting out-of-limit values so the limit checking
 * added in phase 6 has something to trip on.
 *
 * <p>For now it only proves the module builds and runs as its own executable
 * artifact, independent of the server.
 */
public final class SimulatorMain {

    private static final Logger log = LoggerFactory.getLogger(SimulatorMain.class);

    private SimulatorMain() {
        // Utility entry point; not instantiable.
    }

    public static void main(String[] args) {
        log.info("OrbitLink simulator starting (packet generation arrives in phase 4)");
    }
}
