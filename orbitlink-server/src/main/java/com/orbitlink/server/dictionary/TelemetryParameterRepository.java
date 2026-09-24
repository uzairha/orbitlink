package com.orbitlink.server.dictionary;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TelemetryParameterRepository extends JpaRepository<TelemetryParameter, Long> {

    /** Backs the per-packet decode path added in phase 5. */
    List<TelemetryParameter> findByDictionaryIdAndApidOrderByBitOffset(Long dictionaryId, int apid);

    List<TelemetryParameter> findByDictionaryId(Long dictionaryId);
}
