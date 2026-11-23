package io.github.parkiyong.jaybee.api.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable metadata for a deployed archive of process resources.
 */
public record Deployment(
        String id,
        String name,
        DeploymentStatus status,
        Instant deployedAt,
        Set<String> resourceNames
) {
    public Deployment {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(deployedAt, "deployedAt");
        resourceNames = resourceNames == null ? Set.of() : Set.copyOf(resourceNames);
    }

    public boolean isSuspended() {
        return status == DeploymentStatus.SUSPENDED;
    }
}
