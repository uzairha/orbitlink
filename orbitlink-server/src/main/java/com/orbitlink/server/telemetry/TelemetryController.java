package com.orbitlink.server.telemetry;

import java.time.Instant;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read-only views over ingested telemetry. */
@RestController
@RequestMapping("/api/v1/telemetry")
public class TelemetryController {

    private final TelemetrySampleRepository sampleRepository;
    private final TelemetryIngestionServer ingestionServer;
    private final ActiveDictionaryCache dictionaryCache;

    public TelemetryController(TelemetrySampleRepository sampleRepository,
                               TelemetryIngestionServer ingestionServer,
                               ActiveDictionaryCache dictionaryCache) {
        this.sampleRepository = sampleRepository;
        this.ingestionServer = ingestionServer;
        this.dictionaryCache = dictionaryCache;
    }

    public record IngestionStatus(
            boolean listening, int port, long packetsReceived, long packetsRejected,
            String activeDictionary, long storedSamples) {
    }

    public record SampleView(
            String mnemonic, String name, String units,
            long rawValue, double engValue, Instant receivedAt) {
    }

    @GetMapping("/status")
    public IngestionStatus status() {
        return new IngestionStatus(
                ingestionServer.boundPort() > 0,
                ingestionServer.boundPort(),
                ingestionServer.packetsReceived(),
                ingestionServer.packetsRejected(),
                dictionaryCache.current().version(),
                sampleRepository.count());
    }

    /**
     * Current value of every parameter.
     *
     * <p>Transactional because the view reads each sample's parameter, which is
     * a lazy association — the same trap the dictionary listing hit in phase 3.
     * Here a transaction is the right answer rather than a projection, because
     * the endpoint genuinely needs fields from both sides and the result set is
     * bounded by the parameter count, not by the sample count.
     */
    @GetMapping("/latest")
    @Transactional(readOnly = true)
    public List<SampleView> latest() {
        return sampleRepository.findLatestPerParameter().stream()
                .map(sample -> new SampleView(
                        sample.getParameter().getMnemonic(),
                        sample.getParameter().getName(),
                        sample.getParameter().getUnits(),
                        sample.getRawValue(),
                        sample.getEngValue(),
                        sample.getReceivedAt()))
                .sorted((a, b) -> a.mnemonic().compareTo(b.mnemonic()))
                .toList();
    }
}
