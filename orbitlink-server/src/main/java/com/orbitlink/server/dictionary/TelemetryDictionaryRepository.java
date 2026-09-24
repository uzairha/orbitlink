package com.orbitlink.server.dictionary;

import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TelemetryDictionaryRepository extends JpaRepository<TelemetryDictionary, Long> {

    Optional<TelemetryDictionary> findByVersion(String version);

    boolean existsByVersion(String version);

    /**
     * The decoder needs the parameters too, so this fetches them in one query.
     * Without the entity graph, touching getParameters() would fire a second
     * query per dictionary — the N+1 problem in miniature.
     */
    @EntityGraph(attributePaths = "parameters")
    Optional<TelemetryDictionary> findByActiveIsTrue();
}
