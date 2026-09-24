package com.orbitlink.server.dictionary;

/**
 * Read-only summary of a dictionary, built directly by a JPQL constructor
 * expression.
 *
 * <p>This exists because listing dictionaries needs a parameter <em>count</em>,
 * not the parameters themselves. Loading the entity and calling
 * {@code getParameters().size()} would either fail with a
 * LazyInitializationException outside a transaction, or — with
 * {@code open-in-view} on — quietly fire one extra SELECT per dictionary and
 * pull every parameter row into memory just to count them.
 *
 * <p>Projecting in the query asks the database for exactly what the endpoint
 * needs, which is both correct and a single round trip.
 */
public record DictionarySummaryView(
        String version,
        String description,
        boolean active,
        long parameterCount) {
}
