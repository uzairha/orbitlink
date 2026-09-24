package com.orbitlink.server.telemetry;

import com.orbitlink.server.dictionary.TelemetryParameter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Decodes one packet against the active dictionary and stores its samples.
 *
 * <p>Split from {@link TelemetryIngestionServer} so that the socket handling
 * and the persistence are independently testable, and so this can be
 * transactional. A transaction cannot usefully wrap the server's accept loop,
 * which lives for the whole life of a connection.
 */
@Service
public class TelemetryIngestionService {

    private static final Logger log = LoggerFactory.getLogger(TelemetryIngestionService.class);

    private final ActiveDictionaryCache dictionaryCache;
    private final TelemetrySampleRepository sampleRepository;

    public TelemetryIngestionService(ActiveDictionaryCache dictionaryCache,
                                     TelemetrySampleRepository sampleRepository) {
        this.dictionaryCache = dictionaryCache;
        this.sampleRepository = sampleRepository;
    }

    /**
     * One transaction per packet.
     *
     * <p>Per-packet rather than per-sample: a packet is the unit that actually
     * arrived, so either all of its parameters are recorded or none are, and a
     * partially stored packet can never be mistaken for a real measurement.
     * Per-sample transactions would also mean ten round trips where one will do.
     *
     * @return the values decoded, for callers that want to react to them —
     *         phase 6's limit checking will
     */
    @Transactional
    public List<DecodedValue> ingest(SpacePacketHeader header, byte[] dataField) {
        ActiveDictionaryCache.Snapshot dictionary = dictionaryCache.current();

        if (!dictionary.isPresent()) {
            log.warn("Dropping apid {} packet: no active dictionary", header.apid());
            return List.of();
        }

        List<TelemetryParameter> parameters = dictionary.parametersFor(header.apid());
        if (parameters.isEmpty()) {
            // Not an error: a spacecraft legitimately emits packets a given
            // ground dictionary version does not describe.
            log.debug("No parameters defined for apid {} in dictionary {}",
                    header.apid(), dictionary.version());
            return List.of();
        }

        List<DecodedValue> decoded = PacketDecoder.decode(dataField, parameters);

        Instant receivedAt = Instant.now();
        List<TelemetrySample> samples = new ArrayList<>(decoded.size());
        for (DecodedValue value : decoded) {
            samples.add(new TelemetrySample(
                    value.parameter(),
                    receivedAt,
                    header.apid(),
                    header.sequenceCount(),
                    value.rawValue(),
                    value.engValue()));
        }

        // saveAll batches the inserts into one flush rather than one statement
        // per sample.
        sampleRepository.saveAll(samples);

        return decoded;
    }
}
