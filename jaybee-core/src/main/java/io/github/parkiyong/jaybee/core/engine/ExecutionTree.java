package io.github.parkiyong.jaybee.core.engine;

import io.github.parkiyong.jaybee.api.model.ActivityDefinition;
import io.github.parkiyong.jaybee.api.model.Execution;
import io.github.parkiyong.jaybee.api.model.ExecutionStatus;
import io.github.parkiyong.jaybee.api.model.MutableExecution;
import io.github.parkiyong.jaybee.api.model.MutableProcessInstance;
import io.github.parkiyong.jaybee.api.model.ProcessDefinition;
import io.github.parkiyong.jaybee.api.model.ProcessInstance;
import io.github.parkiyong.jaybee.api.spi.ExecutionNodeData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * In-transaction mutable execution tree for a single process instance, plus immutable snapshot factory.
 */
final class ExecutionTree {

    private final ProcessDefinition definition;
    private final MutableNode root;
    private final Map<String, MutableNode> byId = new LinkedHashMap<>();

    private ExecutionTree(ProcessDefinition definition, MutableNode root) {
        this.definition = Objects.requireNonNull(definition, "definition");
        this.root = Objects.requireNonNull(root, "root");
        this.byId.put(root.id, root);
    }

    static ExecutionTree createNew(
            ProcessDefinition definition,
            String processInstanceId,
            String businessKey,
            Map<String, ?> initialVariables
    ) {
        MutableNode root = new MutableNode(
                null,
                processInstanceId,
                null,
                definition.getId(),
                businessKey,
                null,
                ExecutionStatus.RUNNING,
                null,
                0,
                null,
                initialVariables,
                -1L
        );
        ExecutionTree tree = new ExecutionTree(definition, root);
        root.bindTree(tree);
        return tree;
    }

    static ExecutionTree fromData(ProcessDefinition definition, List<ExecutionNodeData> nodes) {
        Map<String, MutableNode> map = new LinkedHashMap<>();
        MutableNode root = null;
        for (ExecutionNodeData data : nodes) {
            MutableNode node = new MutableNode(
                    null,
                    data.id(),
                    null,
                    data.processDefinitionId(),
                    data.key(),
                    data.name(),
                    data.status(),
                    data.terminalOutcome(),
                    data.priority(),
                    data.currentActivityName(),
                    data.variables(),
                    data.version()
            );
            map.put(node.id, node);
            if (data.isRoot()) {
                root = node;
            }
        }
        if (root == null) {
            throw new IllegalStateException("Execution tree is missing a root node");
        }
        ExecutionTree tree = new ExecutionTree(definition, root);
        for (MutableNode node : map.values()) {
            node.bindTree(tree);
            tree.byId.put(node.id, node);
        }
        for (ExecutionNodeData data : nodes) {
            if (data.parentId() != null) {
                MutableNode child = map.get(data.id());
                MutableNode parent = map.get(data.parentId());
                if (parent != null) {
                    child.parent = parent;
                    parent.children.add(child);
                }
            }
        }
        return tree;
    }

    ProcessDefinition definition() {
        return definition;
    }

    MutableNode root() {
        return root;
    }

    MutableNode findNode(String executionId) {
        return byId.get(executionId);
    }

    MutableNode createChild(MutableNode parent, String childId, String branchName, ExecutionStatus status) {
        MutableNode child = new MutableNode(
                this,
                childId,
                parent,
                definition.getId(),
                null,
                branchName,
                status,
                null,
                parent.priority,
                parent.currentActivityName,
                null,
                root.version
        );
        parent.children.add(child);
        byId.put(childId, child);
        return child;
    }

    void removeSubtree(MutableNode node) {
        if (node.parent != null) {
            node.parent.children.remove(node);
        }
        removeRecursivelyFromIndex(node);
    }

    private void removeRecursivelyFromIndex(MutableNode node) {
        byId.remove(node.id);
        for (MutableNode child : new ArrayList<>(node.children)) {
            removeRecursivelyFromIndex(child);
        }
        node.children.clear();
    }

    List<ExecutionNodeData> toData() {
        List<ExecutionNodeData> result = new ArrayList<>();
        collectData(root, result);
        return result;
    }

    private void collectData(MutableNode node, List<ExecutionNodeData> out) {
        out.add(new ExecutionNodeData(
                node.id,
                root.id,
                node.parent == null ? null : node.parent.id,
                node.processDefinitionId,
                node.key,
                node.name,
                node.status,
                node.terminalOutcome,
                node.priority,
                node.currentActivityName,
                node.localVariables,
                node.version
        ));
        for (MutableNode child : node.children) {
            collectData(child, out);
        }
    }

    static ProcessInstance toSnapshot(List<ExecutionNodeData> nodes) {
        Map<String, SnapshotNode> byId = new LinkedHashMap<>();
        SnapshotNode root = null;
        for (ExecutionNodeData d : nodes) {
            SnapshotNode sn = new SnapshotNode(d);
            byId.put(d.id(), sn);
            if (d.isRoot()) {
                root = sn;
            }
        }
        if (root == null) {
            throw new IllegalStateException("Execution tree snapshot missing root node");
        }
        for (ExecutionNodeData d : nodes) {
            SnapshotNode sn = byId.get(d.id());
            sn.processInstance = root;
            if (d.parentId() != null) {
                SnapshotNode parent = byId.get(d.parentId());
                if (parent != null) {
                    sn.parent = parent;
                    parent.children.add(sn);
                }
            }
        }
        for (SnapshotNode sn : byId.values()) {
            sn.freeze();
        }
        return root;
    }

    static Execution toExecutionSnapshot(List<ExecutionNodeData> nodes, String executionId) {
        ProcessInstance root = toSnapshot(nodes);
        return findInSnapshot(root, executionId);
    }

    private static Execution findInSnapshot(Execution node, String executionId) {
        if (node.getId().equals(executionId)) {
            return node;
        }
        for (Execution child : node.getExecutions()) {
            Execution found = findInSnapshot(child, executionId);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    static final class MutableNode implements MutableProcessInstance {
        private ExecutionTree tree;
        private final String id;
        private MutableNode parent;
        private final String processDefinitionId;
        private final String key;
        private final String name;
        private ExecutionStatus status;
        private String terminalOutcome;
        private int priority;
        private String currentActivityName;
        private final Map<String, Object> localVariables = new LinkedHashMap<>();
        private final List<MutableNode> children = new ArrayList<>();
        private long version;

        MutableNode(
                ExecutionTree tree,
                String id,
                MutableNode parent,
                String processDefinitionId,
                String key,
                String name,
                ExecutionStatus status,
                String terminalOutcome,
                int priority,
                String currentActivityName,
                Map<String, ?> initialVariables,
                long version
        ) {
            this.tree = tree;
            this.id = id;
            this.parent = parent;
            this.processDefinitionId = processDefinitionId;
            this.key = key;
            this.name = name;
            this.status = Objects.requireNonNull(status, "status");
            this.terminalOutcome = terminalOutcome;
            this.priority = priority;
            this.currentActivityName = currentActivityName;
            this.version = version;
            if (initialVariables != null) {
                this.localVariables.putAll(initialVariables);
            }
        }

        void bindTree(ExecutionTree tree) {
            this.tree = tree;
        }

        List<MutableNode> mutableChildren() {
            return children;
        }

        void setCurrentActivityName(String activityName) {
            this.currentActivityName = activityName;
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public String getKey() {
            return key;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public ExecutionStatus getStatus() {
            return status;
        }

        @Override
        public Optional<String> getTerminalOutcome() {
            return Optional.ofNullable(terminalOutcome);
        }

        @Override
        public void setStatus(ExecutionStatus status) {
            this.status = Objects.requireNonNull(status, "status");
        }

        @Override
        public void setTerminalOutcome(String terminalOutcome) {
            this.terminalOutcome = terminalOutcome;
        }

        @Override
        public int getPriority() {
            return priority;
        }

        @Override
        public void setPriority(int priority) {
            this.priority = priority;
        }

        @Override
        public String getProcessDefinitionId() {
            return processDefinitionId;
        }

        @Override
        public String getCurrentActivityName() {
            return currentActivityName;
        }

        @Override
        public ActivityDefinition getActivityDefinition() {
            return currentActivityName == null ? null : tree.definition.getActivity(currentActivityName);
        }

        @Override
        public ProcessDefinition getProcessDefinition() {
            return tree.definition;
        }

        @Override
        public MutableNode getParent() {
            return parent;
        }

        @Override
        public MutableProcessInstance getProcessInstance() {
            return tree.root;
        }

        @Override
        public Collection<? extends Execution> getExecutions() {
            return Collections.unmodifiableList(children);
        }

        @Override
        public Map<String, ? extends Execution> getExecutionsMap() {
            Map<String, Execution> map = new LinkedHashMap<>();
            for (MutableNode child : children) {
                if (child.name != null) {
                    map.put(child.name, child);
                }
            }
            return Collections.unmodifiableMap(map);
        }

        @Override
        public MutableExecution getExecution(String name) {
            for (MutableNode child : children) {
                if (Objects.equals(child.name, name)) {
                    return child;
                }
            }
            return null;
        }

        @Override
        public boolean hasExecution(String name) {
            return getExecution(name) != null;
        }

        @Override
        public boolean isProcessInstance() {
            return parent == null;
        }

        @Override
        public boolean isEnded() {
            return status.isTerminal();
        }

        @Override
        public boolean isSuspended() {
            return status == ExecutionStatus.SUSPENDED;
        }

        @Override
        public boolean isActive(String activityName) {
            return findActiveActivityNames().contains(activityName);
        }

        @Override
        public Set<String> findActiveActivityNames() {
            Set<String> names = new LinkedHashSet<>();
            collectActiveActivityNames(this, names);
            return Collections.unmodifiableSet(names);
        }

        private void collectActiveActivityNames(MutableNode node, Set<String> out) {
            if (node.status.isRunning() && node.currentActivityName != null) {
                out.add(node.currentActivityName);
            }
            for (MutableNode child : node.children) {
                collectActiveActivityNames(child, out);
            }
        }

        @Override
        public MutableExecution findActiveExecutionIn(String activityName) {
            if (status.isRunning() && Objects.equals(currentActivityName, activityName)) {
                return this;
            }
            for (MutableNode child : children) {
                MutableExecution found = child.findActiveExecutionIn(activityName);
                if (found != null) {
                    return found;
                }
            }
            return null;
        }

        @Override
        public long getVersion() {
            return version;
        }

        @Override
        public Object getVariable(String key) {
            MutableNode cur = this;
            while (cur != null) {
                if (cur.localVariables.containsKey(key)) {
                    return cur.localVariables.get(key);
                }
                cur = cur.parent;
            }
            return null;
        }

        @Override
        public boolean hasVariable(String key) {
            MutableNode cur = this;
            while (cur != null) {
                if (cur.localVariables.containsKey(key)) {
                    return true;
                }
                cur = cur.parent;
            }
            return false;
        }

        @Override
        public boolean hasVariables() {
            MutableNode cur = this;
            while (cur != null) {
                if (!cur.localVariables.isEmpty()) {
                    return true;
                }
                cur = cur.parent;
            }
            return false;
        }

        @Override
        public Set<String> getVariableKeys() {
            Set<String> keys = new LinkedHashSet<>();
            MutableNode cur = this;
            while (cur != null) {
                keys.addAll(cur.localVariables.keySet());
                cur = cur.parent;
            }
            return Collections.unmodifiableSet(keys);
        }

        @Override
        public Map<String, Object> getVariables() {
            List<MutableNode> chain = new ArrayList<>();
            MutableNode cur = this;
            while (cur != null) {
                chain.add(cur);
                cur = cur.parent;
            }
            Map<String, Object> merged = new LinkedHashMap<>();
            for (int i = chain.size() - 1; i >= 0; i--) {
                merged.putAll(chain.get(i).localVariables);
            }
            return Collections.unmodifiableMap(merged);
        }

        @Override
        public void setVariable(String key, Object value) {
            MutableNode cur = this;
            while (cur != null) {
                if (cur.localVariables.containsKey(key)) {
                    cur.localVariables.put(key, value);
                    return;
                }
                cur = cur.parent;
            }
            tree.root.localVariables.put(key, value);
        }

        @Override
        public void setVariables(Map<String, ?> variables) {
            if (variables == null) {
                return;
            }
            for (Map.Entry<String, ?> entry : variables.entrySet()) {
                setVariable(entry.getKey(), entry.getValue());
            }
        }

        @Override
        public void createVariable(String key, Object value) {
            localVariables.put(key, value);
        }

        @Override
        public boolean removeVariable(String key) {
            MutableNode cur = this;
            while (cur != null) {
                if (cur.localVariables.containsKey(key)) {
                    cur.localVariables.remove(key);
                    return true;
                }
                cur = cur.parent;
            }
            return false;
        }

        @Override
        public void removeVariables() {
            localVariables.clear();
        }
    }

    private static final class SnapshotNode implements ProcessInstance {
        private final ExecutionNodeData data;
        private SnapshotNode parent;
        private SnapshotNode processInstance;
        private List<SnapshotNode> children = new ArrayList<>();
        private Map<String, SnapshotNode> childrenMap = Collections.emptyMap();

        SnapshotNode(ExecutionNodeData data) {
            this.data = data;
        }

        void freeze() {
            this.children = List.copyOf(this.children);
            Map<String, SnapshotNode> map = new LinkedHashMap<>();
            for (SnapshotNode child : this.children) {
                if (child.getName() != null) {
                    map.put(child.getName(), child);
                }
            }
            this.childrenMap = Collections.unmodifiableMap(map);
        }

        @Override
        public String getId() {
            return data.id();
        }

        @Override
        public String getKey() {
            return data.key();
        }

        @Override
        public String getName() {
            return data.name();
        }

        @Override
        public ExecutionStatus getStatus() {
            return data.status();
        }

        @Override
        public Optional<String> getTerminalOutcome() {
            return Optional.ofNullable(data.terminalOutcome());
        }

        @Override
        public int getPriority() {
            return data.priority();
        }

        @Override
        public String getProcessDefinitionId() {
            return data.processDefinitionId();
        }

        @Override
        public String getCurrentActivityName() {
            return data.currentActivityName();
        }

        @Override
        public Execution getParent() {
            return parent;
        }

        @Override
        public ProcessInstance getProcessInstance() {
            return processInstance;
        }

        @Override
        public Collection<? extends Execution> getExecutions() {
            return children;
        }

        @Override
        public Map<String, ? extends Execution> getExecutionsMap() {
            return childrenMap;
        }

        @Override
        public Execution getExecution(String name) {
            return childrenMap.get(name);
        }

        @Override
        public boolean hasExecution(String name) {
            return childrenMap.containsKey(name);
        }

        @Override
        public boolean isProcessInstance() {
            return parent == null;
        }

        @Override
        public boolean isEnded() {
            return data.status().isTerminal();
        }

        @Override
        public boolean isSuspended() {
            return data.status() == ExecutionStatus.SUSPENDED;
        }

        @Override
        public boolean isActive(String activityName) {
            return findActiveActivityNames().contains(activityName);
        }

        @Override
        public Set<String> findActiveActivityNames() {
            Set<String> names = new LinkedHashSet<>();
            collectActive(this, names);
            return Collections.unmodifiableSet(names);
        }

        private void collectActive(SnapshotNode node, Set<String> out) {
            if (node.getStatus().isRunning() && node.getCurrentActivityName() != null) {
                out.add(node.getCurrentActivityName());
            }
            for (SnapshotNode child : node.children) {
                collectActive(child, out);
            }
        }

        @Override
        public Execution findActiveExecutionIn(String activityName) {
            if (getStatus().isRunning() && Objects.equals(getCurrentActivityName(), activityName)) {
                return this;
            }
            for (SnapshotNode child : children) {
                Execution found = child.findActiveExecutionIn(activityName);
                if (found != null) {
                    return found;
                }
            }
            return null;
        }

        @Override
        public long getVersion() {
            return data.version();
        }
    }
}
