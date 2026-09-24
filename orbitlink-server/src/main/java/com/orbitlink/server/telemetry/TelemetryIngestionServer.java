package com.orbitlink.server.telemetry;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import jakarta.annotation.PreDestroy;

/**
 * Accepts spacecraft telemetry over TCP and hands each packet to the decoder.
 *
 * <p>Framing comes from the CCSDS header alone. TCP is a byte stream with no
 * message boundaries, so a read may return half a packet or three at once —
 * this reads exactly 6 octets, learns the payload length from the header, then
 * reads exactly that many. {@code readFully} is what makes that correct;
 * a plain {@code read()} can return short and would silently desynchronise the
 * whole stream from that point on.
 *
 * <p>One virtual thread per connection (Java 21). Virtual threads suit this
 * shape exactly: the work is almost entirely blocking socket reads, and a
 * platform thread per connection would tie up an OS thread doing nothing. The
 * accept loop gets its own thread so it never blocks Spring's startup.
 */
@Component
public class TelemetryIngestionServer {

    private static final Logger log = LoggerFactory.getLogger(TelemetryIngestionServer.class);

    private final TelemetryIngestionService ingestionService;
    private final boolean enabled;
    private final int configuredPort;

    private final AtomicBoolean running = new AtomicBoolean();
    private final LongAdder packetsReceived = new LongAdder();
    private final LongAdder packetsRejected = new LongAdder();

    private ServerSocket serverSocket;
    private ExecutorService connectionExecutor;
    private Thread acceptThread;

    public TelemetryIngestionServer(
            TelemetryIngestionService ingestionService,
            @Value("${orbitlink.ingestion.enabled:true}") boolean enabled,
            @Value("${orbitlink.ingestion.port:9000}") int port) {
        this.ingestionService = ingestionService;
        this.enabled = enabled;
        this.configuredPort = port;
    }

    /**
     * Starts after the context is fully ready, not in @PostConstruct. Binding
     * earlier would let a spacecraft connect while beans were still being
     * created, and the first packet could hit a half-built application.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void start() throws IOException {
        if (!enabled) {
            log.info("Telemetry ingestion disabled");
            return;
        }
        if (!running.compareAndSet(false, true)) {
            return;
        }

        serverSocket = new ServerSocket(configuredPort);
        connectionExecutor = Executors.newVirtualThreadPerTaskExecutor();

        acceptThread = new Thread(this::acceptLoop, "telemetry-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();

        log.info("Telemetry ingestion listening on port {}", boundPort());
    }

    /** The actual bound port, which differs from the configured one when 0 was requested. */
    public int boundPort() {
        return serverSocket != null ? serverSocket.getLocalPort() : -1;
    }

    public long packetsReceived() {
        return packetsReceived.sum();
    }

    public long packetsRejected() {
        return packetsRejected.sum();
    }

    private void acceptLoop() {
        while (running.get()) {
            try {
                Socket socket = serverSocket.accept();
                connectionExecutor.submit(() -> handleConnection(socket));
            } catch (IOException e) {
                // A closed socket during shutdown is expected, not an error.
                if (running.get()) {
                    log.error("Accept failed", e);
                }
            }
        }
    }

    private void handleConnection(Socket socket) {
        String peer = socket.getRemoteSocketAddress().toString();
        log.info("Spacecraft link established from {}", peer);

        try (socket; DataInputStream in = new DataInputStream(socket.getInputStream())) {
            byte[] headerBytes = new byte[SpacePacketHeader.BYTES];

            while (running.get()) {
                try {
                    in.readFully(headerBytes);
                } catch (EOFException e) {
                    break; // clean disconnect
                }

                SpacePacketHeader header = SpacePacketHeader.parse(headerBytes);

                // Read the payload even when the header is rejected: the
                // octets belong to this packet either way, and leaving them in
                // the stream would desynchronise every packet after it.
                byte[] dataField = new byte[header.dataFieldLength()];
                in.readFully(dataField);

                if (!header.isTelemetry()) {
                    packetsRejected.increment();
                    log.warn("Discarding non-telemetry packet: version={} type={} apid={}",
                            header.versionNumber(), header.packetType(), header.apid());
                    continue;
                }

                packetsReceived.increment();
                ingestionService.ingest(header, dataField);
            }
        } catch (IOException e) {
            log.warn("Link from {} dropped: {}", peer, e.getMessage());
        } catch (RuntimeException e) {
            log.error("Ingestion failed for link from {}", peer, e);
        } finally {
            log.info("Spacecraft link from {} closed after {} packets", peer, packetsReceived());
        }
    }

    @PreDestroy
    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException e) {
            log.debug("Error closing server socket", e);
        }
        if (connectionExecutor != null) {
            connectionExecutor.shutdownNow();
            try {
                connectionExecutor.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        log.info("Telemetry ingestion stopped after {} packets ({} rejected)",
                packetsReceived(), packetsRejected());
    }
}
