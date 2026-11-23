package io.github.parkiyong.jaybee.api;

import io.github.parkiyong.jaybee.api.model.Deployment;
import io.github.parkiyong.jaybee.api.model.DeploymentStatus;
import io.github.parkiyong.jaybee.api.query.PageRequest;
import io.github.parkiyong.jaybee.api.query.SortOrder;

import java.util.List;
import java.util.Optional;

/**
 * Type-safe fluent query for searching deployments.
 */
public interface DeploymentQuery {

    enum SortField {
        ID,
        DEPLOYED_AT,
        STATUS
    }

    DeploymentQuery withId(String deploymentId);

    DeploymentQuery withStatus(DeploymentStatus status);

    DeploymentQuery sortBy(SortField field, SortOrder order);

    DeploymentQuery paginate(PageRequest page);

    List<Deployment> fetch();

    Optional<Deployment> fetchOne();

    long count();
}
