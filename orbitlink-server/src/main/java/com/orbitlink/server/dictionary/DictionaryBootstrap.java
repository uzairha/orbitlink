package com.orbitlink.server.dictionary;

import com.orbitlink.server.dictionary.yaml.DictionaryFile;
import java.io.InputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

/**
 * Loads a telemetry dictionary on startup so a fresh database is immediately
 * usable.
 *
 * <p>Deliberately a no-op when any dictionary is already present. Re-loading on
 * every boot would either fail on the version uniqueness constraint or, worse,
 * quietly churn the active dictionary out from under stored telemetry. A
 * dictionary is configuration-controlled data, not seed data to be refreshed.
 *
 * <p>Disable with {@code orbitlink.dictionary.bootstrap-enabled=false} — a
 * real deployment loads dictionaries deliberately through an operator action,
 * not as a side effect of a process restart.
 */
@Component
public class DictionaryBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DictionaryBootstrap.class);

    private final DictionaryLoader loader;
    private final TelemetryDictionaryRepository repository;
    private final ResourceLoader resourceLoader;
    private final boolean enabled;
    private final String location;

    public DictionaryBootstrap(
            DictionaryLoader loader,
            TelemetryDictionaryRepository repository,
            ResourceLoader resourceLoader,
            @Value("${orbitlink.dictionary.bootstrap-enabled:true}") boolean enabled,
            @Value("${orbitlink.dictionary.location:classpath:dictionary/sample-dictionary.yaml}")
            String location) {
        this.loader = loader;
        this.repository = repository;
        this.resourceLoader = resourceLoader;
        this.enabled = enabled;
        this.location = location;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!enabled) {
            log.info("Dictionary bootstrap disabled");
            return;
        }
        if (repository.count() > 0) {
            log.info("Dictionary already present, skipping bootstrap");
            return;
        }

        try (InputStream in = resourceLoader.getResource(location).getInputStream()) {
            DictionaryFile file = loader.parse(in);
            loader.load(file, location, true);
        }
    }
}
