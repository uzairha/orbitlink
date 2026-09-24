package com.orbitlink.simulator;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Spacecraft telemetry simulator.
 *
 * <p>Generates CCSDS space packets from an evolving spacecraft model and
 * streams them to the ground system over TCP, occasionally driving a parameter
 * out of limits so the ground system has anomalies to detect.
 *
 * <pre>
 *   --host           ground system host          (default 127.0.0.1)
 *   --port           ground system TCP port      (default 9000)
 *   --rate           telemetry cycles per second (default 2.0)
 *   --fault-rate     probability of a fault per cycle, 0..1 (default 0.02)
 *   --packets        stop after N packets; 0 means run until interrupted
 *   --seed           RNG seed, for a reproducible run
 *   --dry-run        encode and log packets without connecting
 * </pre>
 */
public final class SimulatorMain {

    private static final Logger log = LoggerFactory.getLogger(SimulatorMain.class);

    private SimulatorMain() {
    }

    public static void main(String[] args) throws Exception {
        Options options = Options.parse(args);
        log.info("Simulator starting: {}", options);

        // A seeded factory makes a run reproducible, which matters when a
        // demo produces an interesting anomaly worth replaying.
        RandomGenerator random = options.seed() != null
                ? RandomGeneratorFactory.of("L64X128MixRandom").create(options.seed())
                : RandomGenerator.getDefault();

        SpacecraftState state = new SpacecraftState(random);

        // A single flag flipped by the shutdown hook, so an interrupt drains
        // the current cycle and closes the socket cleanly rather than killing
        // the process mid-packet and leaving the server a truncated header.
        AtomicBoolean running = new AtomicBoolean(true);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutdown requested, finishing current cycle");
            running.set(false);
        }));

        try (TelemetryTransmitter transmitter = options.dryRun()
                ? null
                : new TelemetryTransmitter(options.host(), options.port(), 5_000)) {

            run(options, random, state, running, transmitter);

        } catch (IOException e) {
            log.error("Transmission failed: {}", e.getMessage());
            System.exit(1);
        }
    }

    private static void run(Options options, RandomGenerator random, SpacecraftState state,
                            AtomicBoolean running, TelemetryTransmitter transmitter)
            throws IOException, InterruptedException {

        double periodSeconds = 1.0 / options.rate();
        long periodMillis = Math.max(1, Math.round(periodSeconds * 1000.0));

        int sequenceCount = 0;
        long packetsSent = 0;
        long faultsInjected = 0;

        while (running.get() && (options.packets() == 0 || packetsSent < options.packets())) {
            state.advance(periodSeconds);

            if (random.nextDouble() < options.faultRate()) {
                state.injectFault(random);
                faultsInjected++;
                log.warn("Injected fault: batteryVoltage={} batteryTemp={}",
                        String.format("%.2f", state.batteryVoltage()),
                        String.format("%.2f", state.batteryTemp()));
            }

            // Both packets share the sequence counter here for simplicity. A
            // real mission keeps an independent counter per APID, because the
            // count is how the ground detects gaps in that specific stream.
            byte[] power = SpacePacket.encode(
                    TelemetryPacketFactory.APID_POWER,
                    sequenceCount,
                    TelemetryPacketFactory.encodePowerPayload(state));
            byte[] attitude = SpacePacket.encode(
                    TelemetryPacketFactory.APID_ATTITUDE,
                    sequenceCount,
                    TelemetryPacketFactory.encodeAttitudePayload(state));

            if (transmitter != null) {
                transmitter.send(power);
                transmitter.send(attitude);
            }
            packetsSent += 2;

            sequenceCount = (sequenceCount + 1) & SpacePacket.MAX_SEQUENCE_COUNT;

            if (packetsSent % 20 == 0) {
                log.info("sent={} faults={} battV={} battTemp={} arrayW={} roll={}",
                        packetsSent, faultsInjected,
                        String.format("%.2f", state.batteryVoltage()),
                        String.format("%.1f", state.batteryTemp()),
                        String.format("%.0f", state.solarArrayPower()),
                        String.format("%.1f", state.roll()));
            }

            Thread.sleep(periodMillis);
        }

        log.info("Simulator stopped: packets={} faults={}", packetsSent, faultsInjected);
    }

    /** Parsed command line. */
    record Options(String host, int port, double rate, double faultRate,
                   long packets, Long seed, boolean dryRun) {

        static Options parse(String[] args) {
            String host = "127.0.0.1";
            int port = 9000;
            double rate = 2.0;
            double faultRate = 0.02;
            long packets = 0;
            Long seed = null;
            boolean dryRun = false;

            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--host" -> host = args[++i];
                    case "--port" -> port = Integer.parseInt(args[++i]);
                    case "--rate" -> rate = Double.parseDouble(args[++i]);
                    case "--fault-rate" -> faultRate = Double.parseDouble(args[++i]);
                    case "--packets" -> packets = Long.parseLong(args[++i]);
                    case "--seed" -> seed = Long.parseLong(args[++i]);
                    case "--dry-run" -> dryRun = true;
                    default -> throw new IllegalArgumentException("unknown option: " + args[i]);
                }
            }
            if (rate <= 0) {
                throw new IllegalArgumentException("--rate must be greater than zero");
            }
            if (faultRate < 0 || faultRate > 1) {
                throw new IllegalArgumentException("--fault-rate must be between 0 and 1");
            }
            return new Options(host, port, rate, faultRate, packets, seed, dryRun);
        }
    }
}
