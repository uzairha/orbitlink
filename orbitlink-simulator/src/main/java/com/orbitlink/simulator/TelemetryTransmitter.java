package com.orbitlink.simulator;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Streams encoded space packets to the ground system over TCP.
 *
 * <p>Packets are written back to back with no framing of their own. None is
 * needed: the CCSDS packet data length field already tells a reader how many
 * octets follow the header, so the stream is self-describing. Adding a length
 * prefix on top would be inventing a second, redundant framing layer.
 *
 * <p>Each packet is flushed as it is produced. Buffering would batch them into
 * fuller TCP segments, but it would also mean telemetry sitting in a local
 * buffer instead of reaching the ground — the wrong trade for a link whose
 * whole purpose is timeliness.
 */
public final class TelemetryTransmitter implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(TelemetryTransmitter.class);

    private final Socket socket;
    private final OutputStream out;

    public TelemetryTransmitter(String host, int port, int connectTimeoutMillis) throws IOException {
        this.socket = new Socket();
        this.socket.connect(new InetSocketAddress(host, port), connectTimeoutMillis);
        // Disable Nagle: it would hold small packets back waiting to coalesce
        // them, adding latency to exactly the traffic this link exists for.
        this.socket.setTcpNoDelay(true);
        this.out = new BufferedOutputStream(socket.getOutputStream());
        log.info("Connected to ground system at {}:{}", host, port);
    }

    public void send(byte[] packet) throws IOException {
        out.write(packet);
        out.flush();
    }

    @Override
    public void close() throws IOException {
        try {
            out.flush();
        } finally {
            socket.close();
        }
    }
}
