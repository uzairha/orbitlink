package com.orbitlink.server.telemetry;

import com.orbitlink.server.dictionary.TelemetryDictionary;
import com.orbitlink.server.dictionary.TelemetryDictionaryRepository;
import com.orbitlink.server.dictionary.TelemetryParameter;
import com.orbitlink.server.dictionary.TelemetryParameterRepository;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Holds the active dictionary's parameters, grouped by APID, in memory.
 *
 * <p>Ingestion decodes every packet against the dictionary. Reading it from the
 * database per packet would put a query — several, given the enum states — on
 * the hot path of a stream that runs continuously. The dictionary changes
 * rarely and only by deliberate operator action, so caching it is the obvious
 * trade.
 *
 * <p>The cache is a single immutable snapshot swapped atomically. Decoding
 * threads therefore always see one coherent dictionary: either wholly the old
 * one or wholly the new one, never a half-applied mixture. That is why this
 * holds an AtomicReference to an immutable map rather than a mutable
 * ConcurrentHashMap that a reload would have to clear and refill.
 */
@Component
public class ActiveDictionaryCache {

    private static final Logger log = LoggerFactory.getLogger(ActiveDictionaryCache.class);

    private final TelemetryDictionaryRepository dictionaryRepository;
    private final TelemetryParameterRepository parameterRepository;

    private final AtomicReference<Snapshot> snapshot = new AtomicReference<>(Snapshot.empty());

    public ActiveDictionaryCache(TelemetryDictionaryRepository dictionaryRepository,
                                 TelemetryParameterRepository parameterRepository) {
        this.dictionaryRepository = dictionaryRepository;
        this.parameterRepository = parameterRepository;
    }

    /** Immutable view of one dictionary version. */
    public record Snapshot(String version, Map<Integer, List<TelemetryParameter>> byApid) {

        static Snapshot empty() {
            return new Snapshot(null, Map.of());
        }

        public boolean isPresent() {
            return version != null;
        }

        public List<TelemetryParameter> parametersFor(int apid) {
            return byApid.getOrDefault(apid, List.of());
        }
    }

    public Snapshot current() {
        return snapshot.get();
    }

    /**
     * Rebuilds the snapshot from the active dictionary.
     *
     * <p>Transactional and eager: the enum states of every parameter are walked
     * here, inside the transaction, so that decoding threads never touch a lazy
     * proxy after the session has closed. That is the same
     * LazyInitializationException trap the dictionary listing endpoint hit, and
     * it would be far worse on a background thread with no request to fail.
     */
    @Transactional(readOnly = true)
    public Snapshot reload() {
        Snapshot loaded = dictionaryRepository.findByActiveIsTrue()
                .map(this::buildSnapshot)
                .orElseGet(() -> {
                    log.warn("No active telemetry dictionary; incoming packets cannot be decoded");
                    return Snapshot.empty();
                });

        snapshot.set(loaded);
        return loaded;
    }

    private Snapshot buildSnapshot(TelemetryDictionary dictionary) {
        List<TelemetryParameter> parameters =
                parameterRepository.findByDictionaryId(dictionary.getId());

        // Force the enum states to load while the session is still open.
        parameters.forEach(parameter -> parameter.getEnumStates().size());

        Map<Integer, List<TelemetryParameter>> byApid = parameters.stream()
                .collect(Collectors.groupingBy(
                        TelemetryParameter::getApid,
                        Collectors.collectingAndThen(Collectors.toList(), List::copyOf)));

        log.info("Cached dictionary {}: {} parameters across {} apids",
                dictionary.getVersion(), parameters.size(), byApid.size());

        return new Snapshot(dictionary.getVersion(), Map.copyOf(byApid));
    }
}
