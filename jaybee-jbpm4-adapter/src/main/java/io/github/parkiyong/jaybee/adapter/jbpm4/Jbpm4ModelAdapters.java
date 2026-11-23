package io.github.parkiyong.jaybee.adapter.jbpm4;

import io.github.parkiyong.jaybee.api.model.ActivityDefinition;
import io.github.parkiyong.jaybee.api.model.Coordinates;
import io.github.parkiyong.jaybee.api.model.ExecutionStatus;
import io.github.parkiyong.jaybee.api.model.MutableExecution;
import io.github.parkiyong.jaybee.api.model.MutableProcessInstance;
import io.github.parkiyong.jaybee.api.model.ProcessDefinition;
import io.github.parkiyong.jaybee.api.model.TransitionDefinition;
import org.jbpm.api.Execution;
import org.jbpm.api.ProcessInstance;
import org.jbpm.api.model.Activity;
import org.jbpm.api.model.ActivityCoordinates;
import org.jbpm.api.model.OpenExecution;
import org.jbpm.api.model.OpenProcessInstance;
import org.jbpm.api.model.Transition;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Wrappers translating Jaybee model types to {@code org.jbpm.api} model interfaces.
 */
final class Jbpm4ModelAdapters {

    record MappedStatus(ExecutionStatus status, String outcome) {
    }

    private Jbpm4ModelAdapters() {
    }

    static String toJbpmState(ExecutionStatus status, Optional<String> outcome) {
        if (status.isTerminal() && outcome.isPresent() && !outcome.get().isBlank()) {
            return outcome.get();
        }
        return switch (status) {
            case NEW -> Execution.STATE_CREATED;
            case RUNNING -> Execution.STATE_ACTIVE_ROOT;
            case RUNNING_BRANCH -> Execution.STATE_ACTIVE_CONCURRENT;
            case WAITING_FOR_BRANCHES -> Execution.STATE_INACTIVE_CONCURRENT_ROOT;
            case AWAITING_JOIN -> Execution.STATE_INACTIVE_JOIN;
            case WAITING_FOR_SCOPE -> Execution.STATE_INACTIVE_SCOPE;
            case SUSPENDED -> Execution.STATE_SUSPENDED;
            case COMPLETED -> Execution.STATE_ENDED;
            case CANCELLED -> "cancel";
            case FAILED -> "error";
        };
    }

    static MappedStatus fromJbpmState(String state) {
        if (state == null || state.isBlank() || Execution.STATE_ENDED.equals(state)) {
            return new MappedStatus(ExecutionStatus.COMPLETED, null);
        }
        if (Execution.STATE_CREATED.equals(state)) {
            return new MappedStatus(ExecutionStatus.NEW, null);
        }
        if (Execution.STATE_ACTIVE_ROOT.equals(state)) {
            return new MappedStatus(ExecutionStatus.RUNNING, null);
        }
        if (Execution.STATE_ACTIVE_CONCURRENT.equals(state)) {
            return new MappedStatus(ExecutionStatus.RUNNING_BRANCH, null);
        }
        if (Execution.STATE_INACTIVE_CONCURRENT_ROOT.equals(state)) {
            return new MappedStatus(ExecutionStatus.WAITING_FOR_BRANCHES, null);
        }
        if (Execution.STATE_INACTIVE_JOIN.equals(state)) {
            return new MappedStatus(ExecutionStatus.AWAITING_JOIN, null);
        }
        if (Execution.STATE_INACTIVE_SCOPE.equals(state)) {
            return new MappedStatus(ExecutionStatus.WAITING_FOR_SCOPE, null);
        }
        if (Execution.STATE_SUSPENDED.equals(state)) {
            return new MappedStatus(ExecutionStatus.SUSPENDED, null);
        }
        if ("cancel".equals(state)) {
            return new MappedStatus(ExecutionStatus.CANCELLED, null);
        }
        if ("error".equals(state)) {
            return new MappedStatus(ExecutionStatus.FAILED, null);
        }
        return new MappedStatus(ExecutionStatus.COMPLETED, state);
    }

    static MappedStatus fromJbpmTerminalState(String state) {
        if (state == null || state.isBlank() || Execution.STATE_ENDED.equals(state)) {
            return new MappedStatus(ExecutionStatus.COMPLETED, null);
        }
        if ("cancel".equals(state)) {
            return new MappedStatus(ExecutionStatus.CANCELLED, null);
        }
        if ("error".equals(state)) {
            return new MappedStatus(ExecutionStatus.FAILED, null);
        }
        return new MappedStatus(ExecutionStatus.COMPLETED, state);
    }

    static Execution wrapExecution(io.github.parkiyong.jaybee.api.model.Execution execution) {
        if (execution == null) {
            return null;
        }
        if (execution instanceof io.github.parkiyong.jaybee.api.model.ProcessInstance pi) {
            return new Jbpm4ProcessInstance(pi);
        }
        return new Jbpm4Execution(execution);
    }

    static ProcessInstance wrapProcessInstance(io.github.parkiyong.jaybee.api.model.ProcessInstance pi) {
        return pi == null ? null : new Jbpm4ProcessInstance(pi);
    }

    static OpenExecution wrapOpenExecution(MutableExecution execution) {
        if (execution == null) {
            return null;
        }
        if (execution instanceof MutableProcessInstance mpi) {
            return new Jbpm4OpenProcessInstance(mpi);
        }
        return new Jbpm4OpenExecution(execution);
    }

    static OpenProcessInstance wrapOpenProcessInstance(MutableProcessInstance pi) {
        return pi == null ? null : new Jbpm4OpenProcessInstance(pi);
    }

    static ActivityCoordinates wrapCoordinates(Coordinates coords) {
        return coords == null ? null : new Jbpm4ActivityCoordinates(coords);
    }

    private static class Jbpm4Execution implements Execution {
        final io.github.parkiyong.jaybee.api.model.Execution delegate;

        Jbpm4Execution(io.github.parkiyong.jaybee.api.model.Execution delegate) {
            this.delegate = delegate;
        }

        @Override
        public String getId() {
            return delegate.getId();
        }

        @Override
        public String getKey() {
            return delegate.getKey();
        }

        @Override
        public String getName() {
            return delegate.getName();
        }

        @Override
        public String getState() {
            return toJbpmState(delegate.getStatus(), delegate.getTerminalOutcome());
        }

        @Override
        public int getPriority() {
            return delegate.getPriority();
        }

        @Override
        public String getProcessDefinitionId() {
            return delegate.getProcessDefinitionId();
        }

        @Override
        public Execution getParent() {
            return wrapExecution(delegate.getParent());
        }

        @Override
        public Execution getProcessInstance() {
            return wrapProcessInstance(delegate.getProcessInstance());
        }

        @Override
        public Execution getSubProcessInstance() {
            return null;
        }

        @Override
        public Collection<? extends Execution> getExecutions() {
            return delegate.getExecutions().stream().map(Jbpm4ModelAdapters::wrapExecution).toList();
        }

        @Override
        public Map<String, ? extends Execution> getExecutionsMap() {
            Map<String, Execution> map = new LinkedHashMap<>();
            for (Map.Entry<String, ? extends io.github.parkiyong.jaybee.api.model.Execution> e
                    : delegate.getExecutionsMap().entrySet()) {
                map.put(e.getKey(), wrapExecution(e.getValue()));
            }
            return Collections.unmodifiableMap(map);
        }

        @Override
        public Execution getExecution(String name) {
            return wrapExecution(delegate.getExecution(name));
        }

        @Override
        public boolean hasExecution(String executionName) {
            return delegate.hasExecution(executionName);
        }

        @Override
        public boolean getIsProcessInstance() {
            return delegate.isProcessInstance();
        }

        @Override
        public boolean isEnded() {
            return delegate.isEnded();
        }

        @Override
        public boolean isSuspended() {
            return delegate.isSuspended();
        }

        @Override
        public boolean isActive(String activityName) {
            return delegate.isActive(activityName);
        }

        @Override
        public Set<String> findActiveActivityNames() {
            return delegate.findActiveActivityNames();
        }

        @Override
        public Execution findActiveExecutionIn(String activityName) {
            return wrapExecution(delegate.findActiveExecutionIn(activityName));
        }
    }

    private static final class Jbpm4ProcessInstance extends Jbpm4Execution implements ProcessInstance {
        Jbpm4ProcessInstance(io.github.parkiyong.jaybee.api.model.ProcessInstance delegate) {
            super(delegate);
        }
    }

    private static class Jbpm4OpenExecution extends Jbpm4Execution implements OpenExecution {
        final MutableExecution mutable;

        Jbpm4OpenExecution(MutableExecution mutable) {
            super(mutable);
            this.mutable = mutable;
        }

        @Override
        public Activity getActivity() {
            ActivityDefinition def = mutable.getActivityDefinition();
            return def == null ? null : new Jbpm4Activity(mutable.getProcessDefinition(), def);
        }

        @Override
        public OpenExecution getParent() {
            return wrapOpenExecution(mutable.getParent());
        }

        @Override
        public OpenProcessInstance getProcessInstance() {
            return wrapOpenProcessInstance(mutable.getProcessInstance());
        }

        @Override
        public OpenExecution getSubProcessInstance() {
            return null;
        }

        @Override
        public OpenExecution getExecution(String name) {
            return wrapOpenExecution(mutable.getExecution(name));
        }

        @Override
        public OpenExecution findActiveExecutionIn(String activityName) {
            return wrapOpenExecution(mutable.findActiveExecutionIn(activityName));
        }

        @Override
        public Object getVariable(String key) {
            return mutable.getVariable(key);
        }

        @Override
        public Set<String> getVariableKeys() {
            return mutable.getVariableKeys();
        }

        @Override
        public Map<String, ?> getVariables() {
            return mutable.getVariables();
        }

        @Override
        public boolean hasVariable(String key) {
            return mutable.hasVariable(key);
        }

        @Override
        public boolean hasVariables() {
            return mutable.hasVariables();
        }

        @Override
        public void setVariable(String key, Object value) {
            mutable.setVariable(key, value);
        }

        @Override
        public void setVariables(Map<String, ?> variables) {
            mutable.setVariables(variables);
        }

        @Override
        public void createVariable(String key, Object value) {
            mutable.createVariable(key, value);
        }

        @Override
        public boolean removeVariable(String key) {
            return mutable.removeVariable(key);
        }

        @Override
        public void removeVariables() {
            mutable.removeVariables();
        }

        @Override
        public void setPriority(int priority) {
            mutable.setPriority(priority);
        }

        @Override
        public void setState(String state) {
            MappedStatus mapped = fromJbpmState(state);
            mutable.setStatus(mapped.status());
            mutable.setTerminalOutcome(mapped.outcome());
        }
    }

    private static final class Jbpm4OpenProcessInstance extends Jbpm4OpenExecution implements OpenProcessInstance {
        Jbpm4OpenProcessInstance(MutableProcessInstance mutable) {
            super(mutable);
        }
    }

    private static final class Jbpm4Activity implements Activity {
        private final ProcessDefinition processDefinition;
        private final ActivityDefinition activity;

        Jbpm4Activity(ProcessDefinition processDefinition, ActivityDefinition activity) {
            this.processDefinition = processDefinition;
            this.activity = activity;
        }

        @Override
        public String getName() {
            return activity.getName();
        }

        @Override
        public String getType() {
            return activity.getType().xmlTag();
        }

        @Override
        public Activity getParentActivity() {
            return null;
        }

        @Override
        public List<? extends Transition> getOutgoingTransitions() {
            return activity.getOutgoingTransitions().stream()
                    .map(t -> new Jbpm4Transition(processDefinition, t))
                    .toList();
        }

        @Override
        public Transition getDefaultOutgoingTransition() {
            TransitionDefinition t = activity.getDefaultOutgoingTransition();
            return t == null ? null : new Jbpm4Transition(processDefinition, t);
        }

        @Override
        public Transition getOutgoingTransition(String transitionName) {
            TransitionDefinition t = activity.getOutgoingTransition(transitionName);
            return t == null ? null : new Jbpm4Transition(processDefinition, t);
        }

        @Override
        public Transition findOutgoingTransition(String transitionName) {
            return getOutgoingTransition(transitionName);
        }

        @Override
        public boolean hasOutgoingTransition(String transitionName) {
            return activity.getOutgoingTransition(transitionName) != null;
        }

        @Override
        public boolean hasOutgoingTransitions() {
            return !activity.getOutgoingTransitions().isEmpty();
        }

        @Override
        public Map<String, ? extends Transition> getOutgoingTransitionsMap() {
            Map<String, Transition> map = new LinkedHashMap<>();
            for (Map.Entry<String, TransitionDefinition> entry : activity.getOutgoingTransitionsMap().entrySet()) {
                map.put(entry.getKey(), new Jbpm4Transition(processDefinition, entry.getValue()));
            }
            return Collections.unmodifiableMap(map);
        }

        @Override
        public List<? extends Transition> getIncomingTransitions() {
            return processDefinition.getIncomingTransitions(activity.getName()).stream()
                    .map(t -> new Jbpm4Transition(processDefinition, t))
                    .toList();
        }

        @Override
        public boolean hasIncomingTransitions() {
            return !processDefinition.getIncomingTransitions(activity.getName()).isEmpty();
        }

        @Override
        public boolean hasActivities() {
            return false;
        }

        @Override
        public List<? extends Activity> getActivities() {
            return List.of();
        }

        @Override
        public boolean hasActivity(String activityName) {
            return false;
        }

        @Override
        public Activity getActivity(String activityName) {
            return null;
        }

        @Override
        public Activity findActivity(String activityName) {
            return activity.getName().equals(activityName) ? this : null;
        }

        @Override
        public Map<String, ? extends Activity> getActivitiesMap() {
            return Map.of();
        }
    }

    private static final class Jbpm4Transition implements Transition {
        private final ProcessDefinition processDefinition;
        private final TransitionDefinition transition;

        Jbpm4Transition(ProcessDefinition processDefinition, TransitionDefinition transition) {
            this.processDefinition = processDefinition;
            this.transition = transition;
        }

        @Override
        public String getName() {
            return transition.name();
        }

        @Override
        public Activity getSource() {
            ActivityDefinition src = processDefinition.getActivity(transition.sourceName());
            return src == null ? null : new Jbpm4Activity(processDefinition, src);
        }

        @Override
        public Activity getDestination() {
            ActivityDefinition dst = processDefinition.getActivity(transition.destinationName());
            return dst == null ? null : new Jbpm4Activity(processDefinition, dst);
        }
    }

    private static final class Jbpm4ActivityCoordinates implements ActivityCoordinates {
        private final Coordinates coordinates;

        Jbpm4ActivityCoordinates(Coordinates coordinates) {
            this.coordinates = coordinates;
        }

        @Override
        public int getX() {
            return coordinates.x();
        }

        @Override
        public int getY() {
            return coordinates.y();
        }

        @Override
        public int getWidth() {
            return coordinates.width();
        }

        @Override
        public int getHeight() {
            return coordinates.height();
        }
    }
}
