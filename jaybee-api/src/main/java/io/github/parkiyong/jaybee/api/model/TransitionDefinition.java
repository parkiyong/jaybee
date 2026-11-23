package io.github.parkiyong.jaybee.api.model;

import java.util.Objects;

/**
 * Directed transition from a source activity to a destination activity, optionally guarded by an expression.
 */
public record TransitionDefinition(
        String name,
        String sourceName,
        String destinationName,
        String conditionExpression
) {
    public TransitionDefinition {
        Objects.requireNonNull(sourceName, "sourceName");
        Objects.requireNonNull(destinationName, "destinationName");
    }
}
