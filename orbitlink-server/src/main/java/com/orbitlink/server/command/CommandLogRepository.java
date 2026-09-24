package com.orbitlink.server.command;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommandLogRepository extends JpaRepository<CommandLog, Long> {

    List<CommandLog> findAllByOrderByIssuedAtDesc(Pageable pageable);

    List<CommandLog> findByStatusOrderByIssuedAtDesc(CommandLog.Status status, Pageable pageable);

    long countByStatus(CommandLog.Status status);
}
