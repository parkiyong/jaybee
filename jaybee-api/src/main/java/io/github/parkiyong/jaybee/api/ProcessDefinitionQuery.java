package io.github.parkiyong.jaybee.api;

import io.github.parkiyong.jaybee.api.model.ProcessDefinition;
import io.github.parkiyong.jaybee.api.query.PageRequest;
import io.github.parkiyong.jaybee.api.query.SortOrder;

import java.util.List;
import java.util.Optional;

/**
 * Type-safe fluent query for searching deployed process definitions.
 */
public interface ProcessDefinitionQuery {

    enum SortField {
        ID,
        KEY,
        NAME,
        VERSION,
        DEPLOYED_AT
    }

    ProcessDefinitionQuery withId(String definitionId);

    ProcessDefinitionQuery withKey(String key);

    ProcessDefinitionQuery withName(String name);

    ProcessDefinitionQuery withNameMatching(String sqlLikePattern);

    ProcessDefinitionQuery inDeployment(String deploymentId);

    ProcessDefinitionQuery withSuspended(boolean suspended);

    ProcessDefinitionQuery sortBy(SortField field, SortOrder order);

    ProcessDefinitionQuery paginate(PageRequest page);

    List<ProcessDefinition> fetch();

    Optional<ProcessDefinition> fetchOne();

    long count();
}
