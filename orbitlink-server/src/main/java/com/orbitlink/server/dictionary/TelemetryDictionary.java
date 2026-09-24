package com.orbitlink.server.dictionary;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * One versioned telemetry dictionary.
 *
 * <p>Dictionaries are immutable once loaded. Changing the spacecraft's
 * telemetry definition means loading a new version, not editing this one, so
 * that telemetry recorded months ago can still be decoded with the dictionary
 * that was active when it arrived.
 */
@Entity
@Table(name = "telemetry_dictionary")
public class TelemetryDictionary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String version;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "source_file")
    private String sourceFile;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "loaded_at", nullable = false)
    private Instant loadedAt = Instant.now();

    /**
     * CascadeType.ALL with orphanRemoval: a parameter has no meaning outside
     * its dictionary, so its lifecycle is entirely owned here. Deleting a
     * dictionary deletes its parameters, which matches the ON DELETE CASCADE
     * in the migration.
     */
    @OneToMany(mappedBy = "dictionary", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TelemetryParameter> parameters = new ArrayList<>();

    protected TelemetryDictionary() {
        // Required by JPA.
    }

    public TelemetryDictionary(String version, String description, String sourceFile) {
        this.version = version;
        this.description = description;
        this.sourceFile = sourceFile;
    }

    /** Adds a parameter and keeps both sides of the association consistent. */
    public void addParameter(TelemetryParameter parameter) {
        parameters.add(parameter);
        parameter.setDictionary(this);
    }

    public Long getId() {
        return id;
    }

    public String getVersion() {
        return version;
    }

    public String getDescription() {
        return description;
    }

    public String getSourceFile() {
        return sourceFile;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getLoadedAt() {
        return loadedAt;
    }

    public List<TelemetryParameter> getParameters() {
        return parameters;
    }
}
