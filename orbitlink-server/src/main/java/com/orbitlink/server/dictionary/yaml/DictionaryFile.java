package com.orbitlink.server.dictionary.yaml;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * The on-disk YAML shape of a telemetry dictionary.
 *
 * <p>These records are deliberately separate from the JPA entities rather than
 * having Jackson populate the entities directly. Two reasons:
 *
 * <ul>
 *   <li>The file format and the database schema change for different reasons
 *       and at different times. Coupling them means every storage tweak is a
 *       breaking change to a file that engineers hand-edit.
 *   <li>Entities carry JPA identity and lifecycle state. Deserialising
 *       untrusted input straight into them invites half-built managed objects.
 * </ul>
 *
 * <p>Records are a good fit here: a parsed file is an immutable value, and
 * phase 3's validation wants exactly that — something it can inspect and
 * report on without any chance of mutating it.
 *
 * <p>failOnUnknownProperties is left ON (the Jackson default) so a typo like
 * "untis:" is a loud parse error rather than a silently ignored field.
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public record DictionaryFile(
        String version,
        String description,
        List<ParameterDefinition> parameters) {
}
