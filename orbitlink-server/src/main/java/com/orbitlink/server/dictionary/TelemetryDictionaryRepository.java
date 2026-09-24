package com.orbitlink.server.dictionary;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TelemetryDictionaryRepository extends JpaRepository<TelemetryDictionary, Long> {

    /**
     * Projects straight to a summary so the listing endpoint never loads the
     * parameter collection. size(d.parameters) becomes a COUNT in SQL rather
     * than materialising the rows.
     */
    @Query("""
            SELECT new com.orbitlink.server.dictionary.DictionarySummaryView(
                d.version, d.description, d.active, size(d.parameters))
            FROM TelemetryDictionary d
            ORDER BY d.loadedAt
            """)
    List<DictionarySummaryView> findAllSummaries();

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
