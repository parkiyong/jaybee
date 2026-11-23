package io.github.parkiyong.jaybee.api.spi;

import io.github.parkiyong.jaybee.api.model.DeploymentStatus;
import io.github.parkiyong.jaybee.api.model.ProcessDefinition;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Pluggable persistence SPI backing deployments, process definitions, and execution trees.
 * Implementations are responsible for transactional isolation and optimistic version verification.
 */
public interface ProcessStore extends AutoCloseable {

    String nextId(String prefix);

    <T> T executeInTransaction(Supplier<T> action);

    <T> T executeForProcessInstance(String processInstanceId, Supplier<T> action);

    // Deployment operations

    void saveDeployment(DeploymentRecord record);

    Optional<DeploymentRecord> findDeploymentById(String deploymentId);

    List<DeploymentRecord> findAllDeployments();

    void updateDeploymentStatus(String deploymentId, DeploymentStatus status);

    void deleteDeployment(String deploymentId);

    // Process definition operations

    int nextProcessDefinitionVersion(String key);

    void saveProcessDefinition(ProcessDefinition definition);

    Optional<ProcessDefinition> findProcessDefinitionById(String processDefinitionId);

    Optional<ProcessDefinition> findLatestProcessDefinitionByKey(String key);

    List<ProcessDefinition> findAllProcessDefinitions();

    List<ProcessDefinition> findProcessDefinitionsByDeploymentId(String deploymentId);

    void updateProcessDefinitionsSuspendedByDeployment(String deploymentId, boolean suspended);

    void deleteProcessDefinitionsByDeployment(String deploymentId);

    // Execution tree operations

    /**
     * Persists a process instance execution tree.
     *
     * @param processInstanceId root process instance ID
     * @param nodes             all execution nodes belonging to the tree
     * @param expectedVersion   expected root version, or {@code -1} when inserting a new tree
     * @return the persisted nodes with incremented version
     */
    List<ExecutionNodeData> saveExecutionTree(
            String processInstanceId,
            List<ExecutionNodeData> nodes,
            long expectedVersion
    );

    Optional<List<ExecutionNodeData>> findExecutionTreeByProcessInstanceId(String processInstanceId);

    Optional<String> findProcessInstanceIdByExecutionId(String executionId);

    List<List<ExecutionNodeData>> findAllExecutionTrees();

    List<String> findProcessInstanceIdsByProcessDefinitionId(String processDefinitionId);

    void deleteExecutionTree(String processInstanceId);

    @Override
    default void close() {
        // no-op by default
    }
}
