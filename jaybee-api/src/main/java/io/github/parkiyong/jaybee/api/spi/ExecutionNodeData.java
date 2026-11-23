package io.github.parkiyong.jaybee.api.spi;

import io.github.parkiyong.jaybee.api.model.ExecutionStatus;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Serializable persistence record for a single execution node within a process instance tree.
 */
public final class ExecutionNodeData {

    private final String id;
    private final String processInstanceId;
    private final String parentId;
    private final String processDefinitionId;
    private final String key;
    private final String name;
    private final ExecutionStatus status;
    private final String terminalOutcome;
    private final int priority;
    private final String currentActivityName;
    private final Map<String, Object> variables;
    private final long version;

    public ExecutionNodeData(
            String id,
            String processInstanceId,
            String parentId,
            String processDefinitionId,
            String key,
            String name,
            ExecutionStatus status,
            String terminalOutcome,
            int priority,
            String currentActivityName,
            Map<String, Object> variables,
            long version
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.processInstanceId = Objects.requireNonNull(processInstanceId, "processInstanceId");
        this.parentId = parentId;
        this.processDefinitionId = Objects.requireNonNull(processDefinitionId, "processDefinitionId");
        this.key = key;
        this.name = name;
        this.status = Objects.requireNonNull(status, "status");
        this.terminalOutcome = terminalOutcome;
        this.priority = priority;
        this.currentActivityName = currentActivityName;
        this.variables = variables == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(variables));
        this.version = version;
    }

    public String id() {
        return id;
    }

    public String processInstanceId() {
        return processInstanceId;
    }

    public String parentId() {
        return parentId;
    }

    public String processDefinitionId() {
        return processDefinitionId;
    }

    public String key() {
        return key;
    }

    public String name() {
        return name;
    }

    public ExecutionStatus status() {
        return status;
    }

    public String terminalOutcome() {
        return terminalOutcome;
    }

    public int priority() {
        return priority;
    }

    public String currentActivityName() {
        return currentActivityName;
    }

    public Map<String, Object> variables() {
        return variables;
    }

    public long version() {
        return version;
    }

    public boolean isRoot() {
        return parentId == null;
    }

    public ExecutionNodeData withVersion(long newVersion) {
        return new ExecutionNodeData(
                id,
                processInstanceId,
                parentId,
                processDefinitionId,
                key,
                name,
                status,
                terminalOutcome,
                priority,
                currentActivityName,
                variables,
                newVersion
        );
    }
}
