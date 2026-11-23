package io.github.parkiyong.jaybee.api;

import io.github.parkiyong.jaybee.api.model.ProcessInstance;
import io.github.parkiyong.jaybee.api.query.PageRequest;
import io.github.parkiyong.jaybee.api.query.SortOrder;

import java.util.List;
import java.util.Optional;

/**
 * Type-safe fluent query for searching active process instances.
 */
public interface ProcessInstanceQuery {

    enum SortField {
        ID,
        BUSINESS_KEY
    }

    ProcessInstanceQuery withId(String instanceId);

    ProcessInstanceQuery withBusinessKey(String businessKey);

    ProcessInstanceQuery withDefinitionId(String definitionId);

    ProcessInstanceQuery withSuspended(boolean suspended);

    ProcessInstanceQuery sortBy(SortField field, SortOrder order);

    ProcessInstanceQuery paginate(PageRequest page);

    List<ProcessInstance> fetch();

    Optional<ProcessInstance> fetchOne();

    long count();
}
