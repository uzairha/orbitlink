package com.orbitlink.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the OrbitLink server.
 *
 * <p>Later phases add the telemetry dictionary (phase 2), the TCP ingestion
 * listener (phase 5), limit checking (phase 6), and the command API (phase 7).
 */
@SpringBootApplication
public class OrbitLinkServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrbitLinkServerApplication.class, args);
    }
}
