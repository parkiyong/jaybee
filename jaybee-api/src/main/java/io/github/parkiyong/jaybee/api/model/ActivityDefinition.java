package io.github.parkiyong.jaybee.api.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable definition of a single node in a process graph.
 */
public final class ActivityDefinition {

    private final String name;
    private final ActivityType type;
    private final Coordinates coordinates;
    private final List<TransitionDefinition> outgoingTransitions;
    private final Map<String, TransitionDefinition> outgoingTransitionsByName;
    private final String decisionExpression;
    private final HandlerDescriptor decisionHandler;
    private final String joinMultiplicityExpression;
    private final ExecutionStatus endStatus;
    private final String endOutcome;
    private final EndScope endScope;

    private ActivityDefinition(Builder builder) {
        this.name = Objects.requireNonNull(builder.name, "name");
        this.type = Objects.requireNonNull(builder.type, "type");
        this.coordinates = builder.coordinates;
        this.outgoingTransitions = List.copyOf(builder.outgoingTransitions);
        Map<String, TransitionDefinition> byName = new LinkedHashMap<>();
        for (TransitionDefinition t : this.outgoingTransitions) {
            if (t.name() != null && !byName.containsKey(t.name())) {
                byName.put(t.name(), t);
            }
        }
        this.outgoingTransitionsByName = Collections.unmodifiableMap(byName);
        this.decisionExpression = builder.decisionExpression;
        this.decisionHandler = builder.decisionHandler;
        this.joinMultiplicityExpression = builder.joinMultiplicityExpression;
        this.endStatus = builder.endStatus;
        this.endOutcome = builder.endOutcome;
        this.endScope = builder.endScope == null ? EndScope.PROCESS_INSTANCE : builder.endScope;
    }

    public static Builder builder(String name, ActivityType type) {
        return new Builder(name, type);
    }

    public String getName() {
        return name;
    }

    public ActivityType getType() {
        return type;
    }

    public Coordinates getCoordinates() {
        return coordinates;
    }

    public List<TransitionDefinition> getOutgoingTransitions() {
        return outgoingTransitions;
    }

    public Map<String, TransitionDefinition> getOutgoingTransitionsMap() {
        return outgoingTransitionsByName;
    }

    public TransitionDefinition getOutgoingTransition(String transitionName) {
        if (transitionName == null) {
            return getDefaultOutgoingTransition();
        }
        return outgoingTransitionsByName.get(transitionName);
    }

    public TransitionDefinition getDefaultOutgoingTransition() {
        return outgoingTransitions.isEmpty() ? null : outgoingTransitions.get(0);
    }

    public String getDecisionExpression() {
        return decisionExpression;
    }

    public HandlerDescriptor getDecisionHandler() {
        return decisionHandler;
    }

    public String getJoinMultiplicityExpression() {
        return joinMultiplicityExpression;
    }

    public ExecutionStatus getEndStatus() {
        return endStatus;
    }

    public String getEndOutcome() {
        return endOutcome;
    }

    public EndScope getEndScope() {
        return endScope;
    }

    public static final class Builder {
        private final String name;
        private final ActivityType type;
        private Coordinates coordinates;
        private List<TransitionDefinition> outgoingTransitions = List.of();
        private String decisionExpression;
        private HandlerDescriptor decisionHandler;
        private String joinMultiplicityExpression;
        private ExecutionStatus endStatus;
        private String endOutcome;
        private EndScope endScope = EndScope.PROCESS_INSTANCE;

        private Builder(String name, ActivityType type) {
            this.name = name;
            this.type = type;
        }

        public Builder coordinates(Coordinates coordinates) {
            this.coordinates = coordinates;
            return this;
        }

        public Builder outgoingTransitions(List<TransitionDefinition> outgoingTransitions) {
            this.outgoingTransitions = outgoingTransitions == null ? List.of() : List.copyOf(outgoingTransitions);
            return this;
        }

        public Builder decisionExpression(String decisionExpression) {
            this.decisionExpression = decisionExpression;
            return this;
        }

        public Builder decisionHandler(HandlerDescriptor decisionHandler) {
            this.decisionHandler = decisionHandler;
            return this;
        }

        public Builder joinMultiplicityExpression(String joinMultiplicityExpression) {
            this.joinMultiplicityExpression = joinMultiplicityExpression;
            return this;
        }

        public Builder endStatus(ExecutionStatus endStatus) {
            this.endStatus = endStatus;
            return this;
        }

        public Builder endOutcome(String endOutcome) {
            this.endOutcome = endOutcome;
            return this;
        }

        public Builder endScope(EndScope endScope) {
            this.endScope = endScope;
            return this;
        }

        public ActivityDefinition build() {
            return new ActivityDefinition(this);
        }
    }
}
