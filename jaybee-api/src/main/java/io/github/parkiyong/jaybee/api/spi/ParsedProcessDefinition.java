package io.github.parkiyong.jaybee.api.spi;

import io.github.parkiyong.jaybee.api.model.ActivityDefinition;

import java.util.List;
import java.util.Objects;

/**
 * Intermediate parsed representation of a process definition prior to deployment versioning.
 */
public record ParsedProcessDefinition(
        String key,
        String name,
        Integer explicitVersion,
        String description,
        String imageResourceName,
        List<ActivityDefinition> activities
) {
    public ParsedProcessDefinition {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(key, "key");
        activities = List.copyOf(Objects.requireNonNull(activities, "activities"));
    }
}
