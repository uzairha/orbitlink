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
import java.util.Arrays;
import java.util.List;

/** One typed, optionally constrained argument of a command. */
@Entity
@Table(name = "command_argument")
public class CommandArgument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "command_id", nullable = false)
    private CommandDefinition command;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_type", nullable = false, length = 32)
    private ArgumentDataType dataType;

    @Column(nullable = false)
    private int position;

    @Column(name = "min_value")
    private Double minValue;

    @Column(name = "max_value")
    private Double maxValue;

    @Column(name = "allowed_values", columnDefinition = "text")
    private String allowedValues;

    @Column(nullable = false)
    private boolean required = true;

    protected CommandArgument() {
        // Required by JPA.
    }

    public CommandArgument(String name, ArgumentDataType dataType, int position) {
        this.name = name;
        this.dataType = dataType;
        this.position = position;
    }

    /** Parsed view of the comma-separated allowed-value list. */
    public List<String> allowedValueList() {
        if (allowedValues == null || allowedValues.isBlank()) {
            return List.of();
        }
        return Arrays.stream(allowedValues.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }

    public Long getId() {
        return id;
    }

    public CommandDefinition getCommand() {
        return command;
    }

    void setCommand(CommandDefinition command) {
        this.command = command;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public ArgumentDataType getDataType() {
        return dataType;
    }

    public int getPosition() {
        return position;
    }

    public Double getMinValue() {
        return minValue;
    }

    public void setMinValue(Double minValue) {
        this.minValue = minValue;
    }

    public Double getMaxValue() {
        return maxValue;
    }

    public void setMaxValue(Double maxValue) {
        this.maxValue = maxValue;
    }

    public String getAllowedValues() {
        return allowedValues;
    }

    public void setAllowedValues(String allowedValues) {
        this.allowedValues = allowedValues;
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }
}
