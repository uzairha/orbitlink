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

    Optional<TelemetryAlarm> findByParameterIdAndClearedAtIsNull();

    @EntityGraph(attributePaths = "parameter")
    List<TelemetryAlarm> findAllByOrderByRaisedAtDesc(Pageable pageable);

    long countByClearedAtIsNull();
}
