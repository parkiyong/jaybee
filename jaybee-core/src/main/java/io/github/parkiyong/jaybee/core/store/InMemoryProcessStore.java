package io.github.parkiyong.jaybee.core.store;

import io.github.parkiyong.jaybee.api.exception.OptimisticLockException;
import io.github.parkiyong.jaybee.api.model.DeploymentStatus;
import io.github.parkiyong.jaybee.api.model.ProcessDefinition;
import io.github.parkiyong.jaybee.api.spi.DeploymentRecord;
import io.github.parkiyong.jaybee.api.spi.ExecutionNodeData;
import io.github.parkiyong.jaybee.api.spi.ProcessStore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Thread-safe in-memory implementation of {@link ProcessStore} with per-process-instance
 * locking and optimistic version checking.
 */
public final class InMemoryProcessStore implements ProcessStore {

    private final AtomicLong idSequence = new AtomicLong(1L);
    private final Map<String, AtomicInteger> keyVersions = new ConcurrentHashMap<>();
    private final Map<String, DeploymentRecord> deployments = new ConcurrentHashMap<>();
    private final Map<String, ProcessDefinition> processDefinitions = new ConcurrentHashMap<>();
    private final Map<String, List<ExecutionNodeData>> executionTrees = new ConcurrentHashMap<>();
    private final Map<String, String> executionToProcessInstance = new ConcurrentHashMap<>();
    private final Map<String, ReentrantLock> instanceLocks = new ConcurrentHashMap<>();
    private final ReentrantLock repoLock = new ReentrantLock();

    @Override
    public String nextId(String prefix) {
        long next = idSequence.getAndIncrement();
        return (prefix == null || prefix.isEmpty()) ? String.valueOf(next) : prefix + "." + next;
    }

    @Override
    public <T> T executeInTransaction(Supplier<T> action) {
        repoLock.lock();
        try {
            return action.get();
        } finally {
            repoLock.unlock();
        }
    }

    @Override
    public <T> T executeForProcessInstance(String processInstanceId, Supplier<T> action) {
        ReentrantLock lock = instanceLocks.computeIfAbsent(processInstanceId, k -> new ReentrantLock());
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void saveDeployment(DeploymentRecord record) {
        deployments.put(record.deployment().id(), record);
    }

    @Override
    public Optional<DeploymentRecord> findDeploymentById(String deploymentId) {
        return Optional.ofNullable(deployments.get(deploymentId));
    }

    @Override
    public List<DeploymentRecord> findAllDeployments() {
        return new ArrayList<>(deployments.values());
    }

    @Override
    public void updateDeploymentStatus(String deploymentId, DeploymentStatus status) {
        deployments.computeIfPresent(deploymentId, (id, existing) -> existing.withStatus(status));
    }

    @Override
    public void deleteDeployment(String deploymentId) {
        deployments.remove(deploymentId);
    }

    @Override
    public int nextProcessDefinitionVersion(String key) {
        return keyVersions.computeIfAbsent(key, k -> new AtomicInteger(0)).incrementAndGet();
    }

    @Override
    public void saveProcessDefinition(ProcessDefinition definition) {
        processDefinitions.put(definition.getId(), definition);
        keyVersions.computeIfAbsent(definition.getKey(), k -> new AtomicInteger(0))
                .accumulateAndGet(definition.getVersion(), Math::max);
    }

    @Override
    public Optional<ProcessDefinition> findProcessDefinitionById(String processDefinitionId) {
        return Optional.ofNullable(processDefinitions.get(processDefinitionId));
    }

    @Override
    public Optional<ProcessDefinition> findLatestProcessDefinitionByKey(String key) {
        return processDefinitions.values().stream()
                .filter(pd -> pd.getKey().equals(key))
                .max(Comparator.comparingInt(ProcessDefinition::getVersion));
    }

    @Override
    public List<ProcessDefinition> findAllProcessDefinitions() {
        return new ArrayList<>(processDefinitions.values());
    }

    @Override
    public List<ProcessDefinition> findProcessDefinitionsByDeploymentId(String deploymentId) {
        return processDefinitions.values().stream()
                .filter(pd -> pd.getDeploymentId().equals(deploymentId))
                .toList();
    }

    @Override
    public void updateProcessDefinitionsSuspendedByDeployment(String deploymentId, boolean suspended) {
        for (Map.Entry<String, ProcessDefinition> entry : processDefinitions.entrySet()) {
            ProcessDefinition pd = entry.getValue();
            if (pd.getDeploymentId().equals(deploymentId)) {
                processDefinitions.put(entry.getKey(), pd.withSuspended(suspended));
            }
        }
    }

    @Override
    public void deleteProcessDefinitionsByDeployment(String deploymentId) {
        processDefinitions.entrySet().removeIf(e -> e.getValue().getDeploymentId().equals(deploymentId));
    }

    @Override
    public List<ExecutionNodeData> saveExecutionTree(
            String processInstanceId,
            List<ExecutionNodeData> nodes,
            long expectedVersion
    ) {
        return executeForProcessInstance(processInstanceId, () -> {
            List<ExecutionNodeData> existing = executionTrees.get(processInstanceId);
            long newVersion;
            if (expectedVersion < 0) {
                if (existing != null) {
                    throw new OptimisticLockException(
                            "Process instance already exists: " + processInstanceId
                    );
                }
                newVersion = 0L;
            } else {
                if (existing == null || existing.isEmpty()) {
                    throw new OptimisticLockException(
                            "Process instance not found for update: " + processInstanceId
                    );
                }
                long currentVersion = findRootVersion(existing);
                if (currentVersion != expectedVersion) {
                    throw new OptimisticLockException(
                            "Optimistic lock conflict for process instance " + processInstanceId
                                    + ": expected version " + expectedVersion + " but was " + currentVersion
                    );
                }
                newVersion = currentVersion + 1L;
            }

            java.util.Set<String> newIds = new java.util.HashSet<>(nodes.size());
            List<ExecutionNodeData> updated = new ArrayList<>(nodes.size());
            for (ExecutionNodeData node : nodes) {
                ExecutionNodeData versioned = node.withVersion(newVersion);
                updated.add(versioned);
                newIds.add(versioned.id());
                executionToProcessInstance.put(versioned.id(), processInstanceId);
            }
            if (existing != null) {
                for (ExecutionNodeData oldNode : existing) {
                    if (!newIds.contains(oldNode.id())) {
                        executionToProcessInstance.remove(oldNode.id());
                    }
                }
            }
            List<ExecutionNodeData> immutable = List.copyOf(updated);
            executionTrees.put(processInstanceId, immutable);
            return immutable;
        });
    }

    @Override
    public Optional<List<ExecutionNodeData>> findExecutionTreeByProcessInstanceId(String processInstanceId) {
        List<ExecutionNodeData> nodes = executionTrees.get(processInstanceId);
        return nodes == null ? Optional.empty() : Optional.of(List.copyOf(nodes));
    }

    @Override
    public Optional<String> findProcessInstanceIdByExecutionId(String executionId) {
        return Optional.ofNullable(executionToProcessInstance.get(executionId));
    }

    @Override
    public List<List<ExecutionNodeData>> findAllExecutionTrees() {
        List<List<ExecutionNodeData>> result = new ArrayList<>();
        for (List<ExecutionNodeData> tree : executionTrees.values()) {
            result.add(List.copyOf(tree));
        }
        return result;
    }

    @Override
    public List<String> findProcessInstanceIdsByProcessDefinitionId(String processDefinitionId) {
        List<String> ids = new ArrayList<>();
        for (Map.Entry<String, List<ExecutionNodeData>> entry : executionTrees.entrySet()) {
            for (ExecutionNodeData node : entry.getValue()) {
                if (node.isRoot() && node.processDefinitionId().equals(processDefinitionId)) {
                    ids.add(entry.getKey());
                    break;
                }
            }
        }
        return ids;
    }

    @Override
    public void deleteExecutionTree(String processInstanceId) {
        executeForProcessInstance(processInstanceId, () -> {
            List<ExecutionNodeData> removed = executionTrees.remove(processInstanceId);
            if (removed != null) {
                for (ExecutionNodeData node : removed) {
                    executionToProcessInstance.remove(node.id());
                }
            }
            return null;
        });
    }

    private long findRootVersion(List<ExecutionNodeData> nodes) {
        for (ExecutionNodeData node : nodes) {
            if (node.isRoot()) {
                return node.version();
            }
        }
        return nodes.get(0).version();
    }
}
