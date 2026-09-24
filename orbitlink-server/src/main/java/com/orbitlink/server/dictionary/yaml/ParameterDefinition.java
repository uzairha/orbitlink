package com.orbitlink.server.dictionary.yaml;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.orbitlink.server.dictionary.ParameterDataType;
import java.util.List;

/**
 * One parameter as written in the YAML file.
 *
 * <p>Every optional field is a boxed type so "absent" and "zero" stay
 * distinguishable — {@code minValue: 0.0} is a real limit, while a missing
 * {@code minValue} means unbounded. Collapsing those into a primitive default
 * would make an unbounded parameter look like one bounded at zero.
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public record ParameterDefinition(
        String mnemonic,
        String name,
        String description,
        Integer apid,
        ParameterDataType dataType,
        Integer bitOffset,
        Integer bitLength,
        String units,
        // Critical (red) limits.
        Double minValue,
        Double maxValue,
        // Warning (yellow) limits, nested inside the critical pair.
        Double warnLow,
        Double warnHigh,
        Double calScale,
        Double calOffset,
        List<EnumStateDefinition> enumStates) {

    /** Calibration defaults to identity when the file omits it. */
    public double calScaleOrDefault() {
        return calScale != null ? calScale : 1.0;
    }

    public double calOffsetOrDefault() {
        return calOffset != null ? calOffset : 0.0;
    }

    public List<EnumStateDefinition> enumStatesOrEmpty() {
        return enumStates != null ? enumStates : List.of();
    }
}
