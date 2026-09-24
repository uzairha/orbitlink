package com.orbitlink.server.command;

/** How a command argument's value should be interpreted and validated. */
public enum ArgumentDataType {
    INTEGER,
    FLOAT,
    BOOLEAN,
    ENUM,
    STRING;

    /** True when min/max range checking is meaningful. */
    public boolean isNumeric() {
        return this == INTEGER || this == FLOAT;
    }
}
