package com.orbitlink.server.command;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A record of one command attempt, accepted or rejected. */
@Entity
@Table(name = "command_log")
public class CommandLog {

    public enum Status {
        ACCEPTED,
        REJECTED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Null when the mnemonic matched no known command. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "command_id")
    private CommandDefinition command;

    @Column(nullable = false, length = 64)
    private String mnemonic;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "issued_by", nullable = false, length = 64)
    private String issuedBy;

    /**
     * Arguments exactly as submitted. JSONB rather than a child table: this is
     * an audit record, read whole and never queried field by field, and its
     * shape varies per command.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String arguments = "{}";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status;

    @Column(name = "rejection_reason", columnDefinition = "text")
    private String rejectionReason;

    protected CommandLog() {
        // Required by JPA.
    }

    public CommandLog(CommandDefinition command, String mnemonic, String issuedBy,
                      String argumentsJson, Status status, String rejectionReason) {
        this.command = command;
        this.mnemonic = mnemonic;
        this.issuedBy = issuedBy;
        this.arguments = argumentsJson;
        this.status = status;
        this.rejectionReason = rejectionReason;
        this.issuedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public CommandDefinition getCommand() {
        return command;
    }

    public String getMnemonic() {
        return mnemonic;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public String getIssuedBy() {
        return issuedBy;
    }

    public String getArguments() {
        return arguments;
    }

    public Status getStatus() {
        return status;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }
}
