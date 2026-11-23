package io.github.parkiyong.jaybee.api;

import io.github.parkiyong.jaybee.api.model.Execution;
import io.github.parkiyong.jaybee.api.model.ExecutionStatus;
import io.github.parkiyong.jaybee.api.model.ProcessInstance;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Native Jaybee service for launching, signalling, inspecting, and managing running process instances.
 */
public interface RuntimeService {

    ProcessInstance startProcess(StartProcessRequest request);

    default ProcessInstance startProcessByKey(String definitionKey) {
        return startProcess(StartProcessRequest.byKey(definitionKey));
    }

    default ProcessInstance startProcessByKey(String definitionKey, Map<String, ?> variables) {
        return startProcess(StartProcessRequest.byKey(definitionKey).withVariables(variables));
    }

    ProcessInstance signal(SignalRequest request);

    default ProcessInstance signal(String executionId) {
        return signal(SignalRequest.forExecution(executionId));
    }

    default ProcessInstance signal(String executionId, String transitionName) {
        return signal(SignalRequest.forExecution(executionId).withTransition(transitionName));
    }

    Optional<ProcessInstance> findInstance(String instanceId);

    Optional<Execution> findExecution(String executionId);

    ProcessInstanceQuery instances();

    Optional<Object> readVariable(String executionId, String name);

    Set<String> listVariableNames(String executionId);

    Map<String, Object> readVariables(String executionId, Collection<String> names);

    void putVariable(String executionId, String name, Object value);

    void putVariables(String executionId, Map<String, ?> variables);

    void declareLocalVariable(String executionId, String name, Object value);

    void declareLocalVariables(String executionId, Map<String, ?> variables);

    void terminateInstance(String instanceId, ExecutionStatus terminalStatus, String outcome);

    default void terminateInstance(String instanceId, ExecutionStatus terminalStatus) {
        terminateInstance(instanceId, terminalStatus, null);
    }

    void removeInstance(String instanceId, boolean cascade);
}
