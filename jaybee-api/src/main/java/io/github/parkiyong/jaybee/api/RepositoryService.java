package io.github.parkiyong.jaybee.api;

import io.github.parkiyong.jaybee.api.model.Coordinates;
import io.github.parkiyong.jaybee.api.model.ProcessDefinition;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Native Jaybee service for deploying, inspecting, suspending, resuming, and removing process definitions.
 */
public interface RepositoryService {

    DeploymentBuilder newDeployment();

    DeploymentQuery deployments();

    ProcessDefinitionQuery definitions();

    Optional<ProcessDefinition> findDefinition(String definitionId);

    void removeDeployment(String deploymentId, boolean cascade);

    void suspendDeployment(String deploymentId);

    void resumeDeployment(String deploymentId);

    Set<String> listResourceNames(String deploymentId);

    Optional<InputStream> openResource(String deploymentId, String resourceName);

    List<String> listStartActivities(String definitionId);

    Optional<Coordinates> findActivityCoordinates(String definitionId, String activityName);
}
