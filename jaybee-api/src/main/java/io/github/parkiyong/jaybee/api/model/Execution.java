package io.github.parkiyong.jaybee.api.model;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Read-only snapshot of an execution path inside a running or completed process instance.
 */
public interface Execution {

    String getId();

    String getKey();

    String getName();

    ExecutionStatus getStatus();

    Optional<String> getTerminalOutcome();

    int getPriority();

    String getProcessDefinitionId();

    String getCurrentActivityName();

    Execution getParent();

    ProcessInstance getProcessInstance();

    Collection<? extends Execution> getExecutions();

    Map<String, ? extends Execution> getExecutionsMap();

    Execution getExecution(String name);

    boolean hasExecution(String name);

    boolean isProcessInstance();

    boolean isEnded();

    boolean isSuspended();

    boolean isActive(String activityName);

    Set<String> findActiveActivityNames();

    Execution findActiveExecutionIn(String activityName);

    long getVersion();
}
