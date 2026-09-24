package com.orbitlink.server.dictionary.yaml;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.orbitlink.server.command.ArgumentDataType;
import java.util.List;

/**
 * Command definitions as written in the dictionary YAML.
 *
 * <p>Commands live in the same file as telemetry parameters and are versioned
 * with them. That is deliberate: the set of commands a spacecraft accepts and
 * the telemetry it returns change together, and splitting them across two
 * independently versioned files invites a mismatch where a command exists that
 * the active configuration does not support.
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public record CommandDefinitionYaml(
        String mnemonic,
        String name,
        String description,
        Integer apid,
        Integer functionCode,
        Boolean hazardous,
        List<ArgumentYaml> arguments) {

    public boolean hazardousOrDefault() {
        return hazardous != null && hazardous;
    }

    public List<ArgumentYaml> argumentsOrEmpty() {
        return arguments != null ? arguments : List.of();
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record ArgumentYaml(
            String name,
            String description,
            ArgumentDataType dataType,
            Double minValue,
            Double maxValue,
            List<String> allowedValues,
            Boolean required) {

        public boolean requiredOrDefault() {
            return required == null || required;
        }

        /** Stored comma-separated; null when no list was given. */
        public String allowedValuesCsv() {
            return allowedValues == null || allowedValues.isEmpty()
                    ? null
                    : String.join(",", allowedValues);
        }
    }
}
