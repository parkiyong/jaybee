package io.github.parkiyong.jaybee.api.model;

import java.util.Map;
import java.util.Set;

/**
 * Mutable execution view passed into user code (such as {@link io.github.parkiyong.jaybee.api.DecisionHandler})
 * during an active engine transaction.
 */
public interface MutableExecution extends Execution {

    ActivityDefinition getActivityDefinition();

    ProcessDefinition getProcessDefinition();

    @Override
    MutableExecution getParent();

    @Override
    MutableProcessInstance getProcessInstance();

    @Override
    MutableExecution getExecution(String name);

    @Override
    MutableExecution findActiveExecutionIn(String activityName);

    Object getVariable(String key);

    Set<String> getVariableKeys();

    Map<String, Object> getVariables();

    boolean hasVariable(String key);

    boolean hasVariables();

    void setVariable(String key, Object value);

    void setVariables(Map<String, ?> variables);

    void createVariable(String key, Object value);

    boolean removeVariable(String key);

    void removeVariables();

    void setPriority(int priority);

    void setStatus(ExecutionStatus status);

    void setTerminalOutcome(String terminalOutcome);
}
