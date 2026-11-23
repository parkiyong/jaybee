package io.github.parkiyong.jaybee.api.spi;

import io.github.parkiyong.jaybee.api.model.Deployment;
import io.github.parkiyong.jaybee.api.model.DeploymentStatus;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Persisted deployment aggregate including raw resource payloads.
 */
public final class DeploymentRecord {

    private final Deployment deployment;
    private final Map<String, byte[]> resources;

    public DeploymentRecord(Deployment deployment, Map<String, byte[]> resources) {
        this.deployment = Objects.requireNonNull(deployment, "deployment");
        Map<String, byte[]> copy = new LinkedHashMap<>();
        if (resources != null) {
            for (Map.Entry<String, byte[]> entry : resources.entrySet()) {
                copy.put(entry.getKey(), entry.getValue().clone());
            }
        }
        this.resources = Collections.unmodifiableMap(copy);
    }

    public Deployment deployment() {
        return deployment;
    }

    public Map<String, byte[]> resources() {
        return resources;
    }

    public byte[] resourceBytes(String resourceName) {
        byte[] bytes = resources.get(resourceName);
        return bytes == null ? null : bytes.clone();
    }

    public DeploymentRecord withStatus(DeploymentStatus newStatus) {
        Deployment updated = new Deployment(
                deployment.id(),
                deployment.name(),
                newStatus,
                deployment.deployedAt(),
                deployment.resourceNames()
        );
        return new DeploymentRecord(updated, resources);
    }
}
