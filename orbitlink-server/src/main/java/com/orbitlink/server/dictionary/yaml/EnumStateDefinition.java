package com.orbitlink.server.dictionary.yaml;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** One {@code rawValue -> label} mapping for an ENUM parameter. */
@JsonIgnoreProperties(ignoreUnknown = false)
public record EnumStateDefinition(Long rawValue, String label) {
}
