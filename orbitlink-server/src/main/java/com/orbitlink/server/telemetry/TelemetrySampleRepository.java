package com.orbitlink.server.telemetry;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TelemetrySampleRepository extends JpaRepository<TelemetrySample, Long> {

    List<TelemetrySample> findByParameterIdAndReceivedAtBetweenOrderByReceivedAtDesc(
            Long parameterId, Instant from, Instant to);

    long countByApid(int apid);

    /**
     * Latest value per parameter, for a dashboard's current-state view.
     *
     * <p>DISTINCT ON is PostgreSQL-specific and deliberately chosen over the
     * portable "join against a grouped max(received_at)" form: it lets the
     * database walk ix_sample_parameter_time and stop at the first row per
     * parameter, instead of aggregating the whole table first.
     */
    @Query(value = """
            SELECT DISTINCT ON (s.parameter_id)
                   s.id, s.parameter_id, s.received_at, s.apid,
                   s.sequence_count, s.raw_value, s.eng_value
            FROM telemetry_sample s
            ORDER BY s.parameter_id, s.received_at DESC
            """, nativeQuery = true)
    List<TelemetrySample> findLatestPerParameter();
}
