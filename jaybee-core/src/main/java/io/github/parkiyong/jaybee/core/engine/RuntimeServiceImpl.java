package io.github.parkiyong.jaybee.core.engine;

import io.github.parkiyong.jaybee.api.ProcessInstanceQuery;
import io.github.parkiyong.jaybee.api.RuntimeService;
import io.github.parkiyong.jaybee.api.SignalRequest;
import io.github.parkiyong.jaybee.api.StartProcessRequest;
import io.github.parkiyong.jaybee.api.exception.JaybeeException;
import io.github.parkiyong.jaybee.api.model.Execution;
import io.github.parkiyong.jaybee.api.model.ExecutionStatus;
import io.github.parkiyong.jaybee.api.model.ProcessDefinition;
import io.github.parkiyong.jaybee.api.model.ProcessInstance;
import io.github.parkiyong.jaybee.api.query.PageRequest;
import io.github.parkiyong.jaybee.api.query.SortOrder;
import io.github.parkiyong.jaybee.api.spi.ExecutionNodeData;
import io.github.parkiyong.jaybee.api.spi.ProcessStore;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Default {@link RuntimeService} managing process instance lifecycle, signalling,
 * hierarchical variables, and per-instance thread-safe atomic execution.
 */
final class RuntimeServiceImpl implements RuntimeService {

    private final ProcessStore store;
    private final ExecutionRuntime runtime;

    RuntimeServiceImpl(ProcessStore store, ExecutionRuntime runtime) {
        this.store = Objects.requireNonNull(store, "store");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
    }

    @Override
    public ProcessInstance startProcess(StartProcessRequest request) {
        Objects.requireNonNull(request, "request");
        ProcessDefinition definition;
        if (request.definitionId() != null && !request.definitionId().isBlank()) {
            definition = store.findProcessDefinitionById(request.definitionId())
                    .orElseThrow(() -> new JaybeeException("Process definition not found: " + request.definitionId()));
        } else {
            definition = store.findLatestProcessDefinitionByKey(request.definitionKey())
                    .orElseThrow(() -> new JaybeeException(
                            "No process definition found for key: " + request.definitionKey()
                    ));
        }
        return startProcessInstance(definition, request.variables(), request.businessKey());
    }

    private ProcessInstance startProcessInstance(
            ProcessDefinition definition,
            Map<String, ?> variables,
            String businessKey
    ) {
        if (definition.isSuspended()) {
            throw new JaybeeException("Cannot start suspended process definition: " + definition.getId());
        }
        String processInstanceId = (businessKey != null && !businessKey.isBlank())
                ? definition.getKey() + "." + businessKey
                : store.nextId(definition.getKey());

        return store.executeForProcessInstance(processInstanceId, () -> {
            ExecutionTree tree = ExecutionTree.createNew(definition, processInstanceId, businessKey, variables);
            runtime.start(tree);
            List<ExecutionNodeData> finalNodes = tree.toData();
            if (tree.root().isEnded()) {
                return ExecutionTree.toSnapshot(finalNodes);
            }
            List<ExecutionNodeData> persisted = store.saveExecutionTree(processInstanceId, finalNodes, -1L);
            return ExecutionTree.toSnapshot(persisted);
        });
    }

    @Override
    public ProcessInstance signal(SignalRequest request) {
        Objects.requireNonNull(request, "request");
        String executionId = request.executionId();
        String processInstanceId = resolveProcessInstanceId(executionId);
        return store.executeForProcessInstance(processInstanceId, () -> {
            List<ExecutionNodeData> stored = store.findExecutionTreeByProcessInstanceId(processInstanceId)
                    .orElseThrow(() -> new JaybeeException("Execution not found: " + executionId));
            long expectedVersion = rootVersion(stored);
            ProcessDefinition def = loadDefinitionForTree(stored);
            ExecutionTree tree = ExecutionTree.fromData(def, stored);
            ExecutionTree.MutableNode target = tree.findNode(executionId);
            if (target == null) {
                throw new JaybeeException("Execution not found: " + executionId);
            }
            runtime.signal(tree, target, request.transitionName(), request.payload());
            List<ExecutionNodeData> updated = tree.toData();
            if (tree.root().isEnded()) {
                store.deleteExecutionTree(processInstanceId);
                return ExecutionTree.toSnapshot(updated);
            }
            List<ExecutionNodeData> persisted = store.saveExecutionTree(processInstanceId, updated, expectedVersion);
            return ExecutionTree.toSnapshot(persisted);
        });
    }

    @Override
    public Optional<ProcessInstance> findInstance(String instanceId) {
        Objects.requireNonNull(instanceId, "instanceId");
        return store.findExecutionTreeByProcessInstanceId(instanceId)
                .map(ExecutionTree::toSnapshot);
    }

    @Override
    public Optional<Execution> findExecution(String executionId) {
        Objects.requireNonNull(executionId, "executionId");
        return store.findProcessInstanceIdByExecutionId(executionId)
                .flatMap(store::findExecutionTreeByProcessInstanceId)
                .map(nodes -> ExecutionTree.toExecutionSnapshot(nodes, executionId));
    }

    @Override
    public ProcessInstanceQuery instances() {
        return new ProcessInstanceQueryImpl();
    }

    @Override
    public Optional<Object> readVariable(String executionId, String name) {
        Objects.requireNonNull(executionId, "executionId");
        Objects.requireNonNull(name, "name");
        String processInstanceId = resolveProcessInstanceId(executionId);
        return store.executeForProcessInstance(processInstanceId, () -> {
            ExecutionTree tree = loadTree(processInstanceId, executionId);
            ExecutionTree.MutableNode node = requireNode(tree, executionId);
            return Optional.ofNullable(node.getVariable(name));
        });
    }

    @Override
    public Set<String> listVariableNames(String executionId) {
        Objects.requireNonNull(executionId, "executionId");
        String processInstanceId = resolveProcessInstanceId(executionId);
        return store.executeForProcessInstance(processInstanceId, () -> {
            ExecutionTree tree = loadTree(processInstanceId, executionId);
            ExecutionTree.MutableNode node = requireNode(tree, executionId);
            return node.getVariableKeys();
        });
    }

    @Override
    public Map<String, Object> readVariables(String executionId, Collection<String> names) {
        Objects.requireNonNull(executionId, "executionId");
        String processInstanceId = resolveProcessInstanceId(executionId);
        return store.executeForProcessInstance(processInstanceId, () -> {
            ExecutionTree tree = loadTree(processInstanceId, executionId);
            ExecutionTree.MutableNode node = requireNode(tree, executionId);
            if (names == null) {
                return node.getVariables();
            }
            Map<String, Object> result = new LinkedHashMap<>();
            for (String name : names) {
                if (node.hasVariable(name)) {
                    result.put(name, node.getVariable(name));
                }
            }
            return result;
        });
    }

    @Override
    public void putVariable(String executionId, String name, Object value) {
        Objects.requireNonNull(executionId, "executionId");
        Objects.requireNonNull(name, "name");
        mutateVariables(executionId, node -> node.setVariable(name, value));
    }

    @Override
    public void putVariables(String executionId, Map<String, ?> variables) {
        Objects.requireNonNull(executionId, "executionId");
        if (variables == null || variables.isEmpty()) {
            return;
        }
        mutateVariables(executionId, node -> node.setVariables(variables));
    }

    @Override
    public void declareLocalVariable(String executionId, String name, Object value) {
        Objects.requireNonNull(executionId, "executionId");
        Objects.requireNonNull(name, "name");
        mutateVariables(executionId, node -> node.createVariable(name, value));
    }

    @Override
    public void declareLocalVariables(String executionId, Map<String, ?> variables) {
        Objects.requireNonNull(executionId, "executionId");
        if (variables == null || variables.isEmpty()) {
            return;
        }
        mutateVariables(executionId, node -> {
            for (Map.Entry<String, ?> entry : variables.entrySet()) {
                node.createVariable(entry.getKey(), entry.getValue());
            }
        });
    }

    @Override
    public void terminateInstance(String instanceId, ExecutionStatus terminalStatus, String outcome) {
        Objects.requireNonNull(instanceId, "instanceId");
        store.executeForProcessInstance(instanceId, () -> {
            List<ExecutionNodeData> stored = store.findExecutionTreeByProcessInstanceId(instanceId)
                    .orElseThrow(() -> new JaybeeException("Process instance not found: " + instanceId));
            ProcessDefinition def = loadDefinitionForTree(stored);
            ExecutionTree tree = ExecutionTree.fromData(def, stored);
            runtime.terminateProcessInstance(tree, terminalStatus, outcome);
            store.deleteExecutionTree(instanceId);
            return null;
        });
    }

    @Override
    public void removeInstance(String instanceId, boolean cascade) {
        Objects.requireNonNull(instanceId, "instanceId");
        if (cascade) {
            store.deleteExecutionTree(instanceId);
            return;
        }
        store.executeForProcessInstance(instanceId, () -> {
            if (store.findExecutionTreeByProcessInstanceId(instanceId).isEmpty()) {
                throw new JaybeeException("Process instance not found: " + instanceId);
            }
            store.deleteExecutionTree(instanceId);
            return null;
        });
    }

    private void mutateVariables(String executionId, Consumer<ExecutionTree.MutableNode> mutator) {
        String processInstanceId = resolveProcessInstanceId(executionId);
        store.executeForProcessInstance(processInstanceId, () -> {
            List<ExecutionNodeData> stored = store.findExecutionTreeByProcessInstanceId(processInstanceId)
                    .orElseThrow(() -> new JaybeeException("Execution not found: " + executionId));
            long expectedVersion = rootVersion(stored);
            ProcessDefinition def = loadDefinitionForTree(stored);
            ExecutionTree tree = ExecutionTree.fromData(def, stored);
            ExecutionTree.MutableNode node = requireNode(tree, executionId);
            mutator.accept(node);
            store.saveExecutionTree(processInstanceId, tree.toData(), expectedVersion);
            return null;
        });
    }

    private String resolveProcessInstanceId(String executionId) {
        return store.findProcessInstanceIdByExecutionId(executionId)
                .orElseThrow(() -> new JaybeeException("Execution not found: " + executionId));
    }

    private ExecutionTree loadTree(String processInstanceId, String executionId) {
        List<ExecutionNodeData> stored = store.findExecutionTreeByProcessInstanceId(processInstanceId)
                .orElseThrow(() -> new JaybeeException("Execution not found: " + executionId));
        return ExecutionTree.fromData(loadDefinitionForTree(stored), stored);
    }

    private ExecutionTree.MutableNode requireNode(ExecutionTree tree, String executionId) {
        ExecutionTree.MutableNode node = tree.findNode(executionId);
        if (node == null) {
            throw new JaybeeException("Execution not found: " + executionId);
        }
        return node;
    }

    private ProcessDefinition loadDefinitionForTree(List<ExecutionNodeData> nodes) {
        String defId = nodes.get(0).processDefinitionId();
        return store.findProcessDefinitionById(defId)
                .orElseThrow(() -> new JaybeeException("Process definition not found: " + defId));
    }

    private long rootVersion(List<ExecutionNodeData> nodes) {
        for (ExecutionNodeData n : nodes) {
            if (n.isRoot()) {
                return n.version();
            }
        }
        return nodes.get(0).version();
    }

    private final class ProcessInstanceQueryImpl implements ProcessInstanceQuery {
        private String instanceId;
        private String businessKey;
        private String definitionId;
        private Boolean suspendedFilter;
        private SortField sortField;
        private SortOrder sortOrder = SortOrder.ASC;
        private PageRequest page = PageRequest.unbounded();

        @Override
        public ProcessInstanceQuery withId(String instanceId) {
            this.instanceId = instanceId;
            return this;
        }

        @Override
        public ProcessInstanceQuery withBusinessKey(String businessKey) {
            this.businessKey = businessKey;
            return this;
        }

        @Override
        public ProcessInstanceQuery withDefinitionId(String definitionId) {
            this.definitionId = definitionId;
            return this;
        }

        @Override
        public ProcessInstanceQuery withSuspended(boolean suspended) {
            this.suspendedFilter = suspended;
            return this;
        }

        @Override
        public ProcessInstanceQuery sortBy(SortField field, SortOrder order) {
            this.sortField = Objects.requireNonNull(field, "field");
            this.sortOrder = Objects.requireNonNull(order, "order");
            return this;
        }

        @Override
        public ProcessInstanceQuery paginate(PageRequest page) {
            this.page = Objects.requireNonNull(page, "page");
            return this;
        }

        @Override
        public List<ProcessInstance> fetch() {
            List<ProcessInstance> filtered = filter();
            if (sortField != null) {
                Comparator<ProcessInstance> cmp = switch (sortField) {
                    case BUSINESS_KEY -> Comparator.comparing(
                            ProcessInstance::getKey,
                            Comparator.nullsLast(Comparator.naturalOrder())
                    );
                    case ID -> Comparator.comparing(ProcessInstance::getId);
                };
                if (sortOrder == SortOrder.DESC) {
                    cmp = cmp.reversed();
                }
                filtered.sort(cmp);
            }
            int offset = page.offset();
            int limit = page.limit();
            if (offset >= filtered.size() || limit == 0) {
                return List.of();
            }
            int end = (limit == Integer.MAX_VALUE)
                    ? filtered.size()
                    : Math.min(filtered.size(), offset + limit);
            return List.copyOf(filtered.subList(offset, end));
        }

        @Override
        public Optional<ProcessInstance> fetchOne() {
            List<ProcessInstance> results = fetch();
            if (results.isEmpty()) {
                return Optional.empty();
            }
            if (results.size() > 1) {
                throw new JaybeeException(
                        "ProcessInstance query did not return a unique result: " + results.size()
                );
            }
            return Optional.of(results.get(0));
        }

        @Override
        public long count() {
            return filter().size();
        }

        private List<ProcessInstance> filter() {
            List<ProcessInstance> results = new ArrayList<>();
            for (List<ExecutionNodeData> treeNodes : store.findAllExecutionTrees()) {
                ProcessInstance pi = ExecutionTree.toSnapshot(treeNodes);
                if (pi.isEnded()) {
                    continue;
                }
                if (instanceId != null && !instanceId.equals(pi.getId())) {
                    continue;
                }
                if (businessKey != null && !businessKey.equals(pi.getKey())) {
                    continue;
                }
                if (definitionId != null && !definitionId.equals(pi.getProcessDefinitionId())) {
                    continue;
                }
                if (suspendedFilter != null && suspendedFilter != pi.isSuspended()) {
                    continue;
                }
                results.add(pi);
            }
            return results;
        }
    }
}
