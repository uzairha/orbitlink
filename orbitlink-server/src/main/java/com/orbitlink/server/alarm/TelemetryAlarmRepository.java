package com.orbitlink.server.alarm;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TelemetryAlarmRepository extends JpaRepository<TelemetryAlarm, Long> {

    /** Fetches the parameter too: every alarm view shows its mnemonic. */
    @EntityGraph(attributePaths = "parameter")
    List<TelemetryAlarm> findByClearedAtIsNullOrderByRaisedAtDesc();

    /**
     * The open alarm for one parameter, if any. Spring Data derives the query
     * from the method name, so the parameterId argument is not optional —
     * without it the name promises a filter the method cannot supply and the
     * whole application context fails to start.
     */
    Optional<TelemetryAlarm> findByParameterIdAndClearedAtIsNull(Long parameterId);

    @EntityGraph(attributePaths = "parameter")
    List<TelemetryAlarm> findAllByOrderByRaisedAtDesc(Pageable pageable);

    long countByClearedAtIsNull();
}
