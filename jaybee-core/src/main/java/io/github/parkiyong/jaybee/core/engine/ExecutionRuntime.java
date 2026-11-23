package io.github.parkiyong.jaybee.core.engine;

import io.github.parkiyong.jaybee.api.DecisionHandler;
import io.github.parkiyong.jaybee.api.exception.JaybeeException;
import io.github.parkiyong.jaybee.api.model.ActivityDefinition;
import io.github.parkiyong.jaybee.api.model.ActivityType;
import io.github.parkiyong.jaybee.api.model.EndScope;
import io.github.parkiyong.jaybee.api.model.ExecutionStatus;
import io.github.parkiyong.jaybee.api.model.ProcessDefinition;
import io.github.parkiyong.jaybee.api.model.TransitionDefinition;
import io.github.parkiyong.jaybee.api.spi.DecisionHandlerBridge;
import io.github.parkiyong.jaybee.api.spi.ExpressionEvaluator;
import io.github.parkiyong.jaybee.api.spi.HandlerResolver;
import io.github.parkiyong.jaybee.api.spi.ProcessStore;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Clean-room token execution runtime implementing jPDL 4.4 core control flow semantics.
 */
final class ExecutionRuntime {

    private static final int MAX_AUTOMATIC_STEPS = 10_000;

    private final ProcessStore store;
    private final ExpressionEvaluator expressionEvaluator;
    private final HandlerResolver handlerResolver;
    private final List<DecisionHandlerBridge> decisionHandlerBridges;

    ExecutionRuntime(
            ProcessStore store,
            ExpressionEvaluator expressionEvaluator,
            HandlerResolver handlerResolver,
            List<DecisionHandlerBridge> decisionHandlerBridges
    ) {
        this.store = store;
        this.expressionEvaluator = expressionEvaluator;
        this.handlerResolver = handlerResolver;
        this.decisionHandlerBridges = List.copyOf(decisionHandlerBridges);
    }

    void start(ExecutionTree tree) {
        List<ActivityDefinition> starts = tree.definition().getStartActivities();
        if (starts.isEmpty()) {
            throw new JaybeeException("Process definition has no start activity: " + tree.definition().getId());
        }
        ActivityDefinition startActivity = starts.get(0);
        Deque<Step> queue = new ArrayDeque<>();
        queue.add(new Step(tree.root(), startActivity));
        drain(tree, queue);
    }

    void signal(ExecutionTree tree, ExecutionTree.MutableNode node, String signalName, Map<String, ?> parameters) {
        if (node.isEnded()) {
            throw new JaybeeException("Cannot signal ended execution: " + node.getId());
        }
        if (node.isSuspended()) {
            throw new JaybeeException("Cannot signal suspended execution: " + node.getId());
        }
        if (!node.getStatus().isRunning()) {
            throw new JaybeeException(
                    "Execution " + node.getId() + " is not in an active wait state (status=" + node.getStatus() + ")"
            );
        }
        ActivityDefinition current = node.getActivityDefinition();
        if (current == null || current.getType() != ActivityType.STATE) {
            throw new JaybeeException(
                    "Execution " + node.getId() + " is not waiting in a state activity (activity="
                            + node.getCurrentActivityName() + ")"
            );
        }

        if (parameters != null && !parameters.isEmpty()) {
            node.setVariables(parameters);
        }

        TransitionDefinition transition = resolveStateTransition(current, signalName);
        if (transition == null) {
            if (signalName != null) {
                throw new JaybeeException(
                        "No outgoing transition named '" + signalName + "' from state '" + current.getName() + "'"
                );
            }
            // No outgoing transition from state: end this execution cleanly
            Deque<Step> queue = new ArrayDeque<>();
            endExecutionBranch(tree, node, ExecutionStatus.COMPLETED, null, queue);
            drain(tree, queue);
            return;
        }

        ActivityDefinition destination = requireActivity(tree.definition(), transition.destinationName());
        Deque<Step> queue = new ArrayDeque<>();
        queue.add(new Step(node, destination));
        drain(tree, queue);
    }

    void terminateProcessInstance(ExecutionTree tree, ExecutionStatus status, String outcome) {
        ExecutionStatus targetStatus = (status == null || !status.isTerminal())
                ? ExecutionStatus.COMPLETED
                : status;
        endSubtree(tree.root(), targetStatus, outcome);
    }

    private record Step(ExecutionTree.MutableNode node, ActivityDefinition activity) {
    }

    private void drain(ExecutionTree tree, Deque<Step> queue) {
        int steps = 0;
        while (!queue.isEmpty()) {
            if (tree.root().isEnded()) {
                return;
            }
            if (++steps > MAX_AUTOMATIC_STEPS) {
                throw new JaybeeException(
                        "Exceeded maximum automatic transitions (" + MAX_AUTOMATIC_STEPS
                                + ") in process " + tree.definition().getId()
                );
            }
            Step step = queue.pollFirst();
            ExecutionTree.MutableNode node = step.node();
            if (node.isEnded() || tree.findNode(node.getId()) == null) {
                continue;
            }
            executeActivity(tree, node, step.activity(), queue);
        }
    }

    private void executeActivity(
            ExecutionTree tree,
            ExecutionTree.MutableNode node,
            ActivityDefinition activity,
            Deque<Step> queue
    ) {
        node.setCurrentActivityName(activity.getName());
        switch (activity.getType()) {
            case START -> executeStart(tree, node, activity, queue);
            case STATE -> {
                // Wait state: execution parks here until signalled.
            }
            case DECISION -> executeDecision(tree, node, activity, queue);
            case FORK -> executeFork(tree, node, activity, queue);
            case JOIN -> executeJoin(tree, node, activity, queue);
            case END, END_CANCEL, END_ERROR -> executeEnd(tree, node, activity, queue);
        }
    }

    private void executeStart(
            ExecutionTree tree,
            ExecutionTree.MutableNode node,
            ActivityDefinition start,
            Deque<Step> queue
    ) {
        TransitionDefinition transition = start.getDefaultOutgoingTransition();
        if (transition == null) {
            throw new JaybeeException("Start activity '" + start.getName() + "' has no outgoing transition");
        }
        queue.addFirst(new Step(node, requireActivity(tree.definition(), transition.destinationName())));
    }

    private void executeDecision(
            ExecutionTree tree,
            ExecutionTree.MutableNode node,
            ActivityDefinition decision,
            Deque<Step> queue
    ) {
        TransitionDefinition chosen;
        if (decision.getDecisionHandler() != null) {
            Object handler = handlerResolver.resolve(
                    decision.getDecisionHandler(),
                    expressionEvaluator,
                    node::getVariable
            );
            String transitionName = invokeDecisionHandler(handler, node);
            chosen = requireNamedTransition(decision, transitionName);
        } else if (decision.getDecisionExpression() != null && !decision.getDecisionExpression().isBlank()) {
            String transitionName = expressionEvaluator.evaluateString(
                    decision.getDecisionExpression(),
                    node::getVariable
            );
            chosen = requireNamedTransition(decision, transitionName);
        } else {
            chosen = evaluateConditionTransitions(decision, node);
        }

        queue.addFirst(new Step(node, requireActivity(tree.definition(), chosen.destinationName())));
    }

    private String invokeDecisionHandler(Object handler, ExecutionTree.MutableNode node) {
        if (handler instanceof DecisionHandler nativeHandler) {
            return nativeHandler.decide(node);
        }
        for (DecisionHandlerBridge bridge : decisionHandlerBridges) {
            if (bridge.supports(handler)) {
                return bridge.invoke(handler, node);
            }
        }
        throw new JaybeeException(
                "Unsupported decision handler type: " + handler.getClass().getName()
                        + " (register a DecisionHandlerBridge or implement " + DecisionHandler.class.getName() + ")"
        );
    }

    private TransitionDefinition requireNamedTransition(ActivityDefinition activity, String transitionName) {
        if (transitionName == null) {
            throw new JaybeeException(
                    "Decision '" + activity.getName() + "' evaluated to null transition name"
            );
        }
        TransitionDefinition chosen = activity.getOutgoingTransition(transitionName);
        if (chosen == null) {
            throw new JaybeeException(
                    "Decision '" + activity.getName() + "' selected unknown outgoing transition '" + transitionName + "'"
            );
        }
        return chosen;
    }

    private TransitionDefinition evaluateConditionTransitions(
            ActivityDefinition decision,
            ExecutionTree.MutableNode node
    ) {
        TransitionDefinition fallback = null;
        for (TransitionDefinition transition : decision.getOutgoingTransitions()) {
            String cond = transition.conditionExpression();
            if (cond == null || cond.isBlank()) {
                if (fallback == null) {
                    fallback = transition;
                }
            } else if (expressionEvaluator.evaluateBoolean(cond, node::getVariable)) {
                return transition;
            }
        }
        if (fallback != null) {
            return fallback;
        }
        throw new JaybeeException(
                "No condition matched and no default transition exists on decision '" + decision.getName() + "'"
        );
    }

    private void executeFork(
            ExecutionTree tree,
            ExecutionTree.MutableNode node,
            ActivityDefinition fork,
            Deque<Step> queue
    ) {
        List<TransitionDefinition> outgoing = fork.getOutgoingTransitions();
        if (outgoing.isEmpty()) {
            throw new JaybeeException("Fork activity '" + fork.getName() + "' has no outgoing transitions");
        }
        node.setStatus(ExecutionStatus.WAITING_FOR_BRANCHES);

        List<Step> branchSteps = new ArrayList<>(outgoing.size());
        int idx = 1;
        for (TransitionDefinition transition : outgoing) {
            String branchName = (transition.name() != null && !transition.name().isBlank())
                    ? transition.name()
                    : "branch-" + idx;
            idx++;
            String childId = store.nextId(tree.root().getId());
            ExecutionTree.MutableNode child = tree.createChild(
                    node,
                    childId,
                    branchName,
                    ExecutionStatus.RUNNING_BRANCH
            );
            ActivityDefinition dest = requireActivity(tree.definition(), transition.destinationName());
            branchSteps.add(new Step(child, dest));
        }
        // Enqueue in declaration order
        for (Step branchStep : branchSteps) {
            queue.addLast(branchStep);
        }
    }

    private void executeJoin(
            ExecutionTree tree,
            ExecutionTree.MutableNode node,
            ActivityDefinition join,
            Deque<Step> queue
    ) {
        ExecutionTree.MutableNode parent = node.getParent();
        if (parent == null) {
            // Join reached without an active fork parent: proceed directly
            TransitionDefinition out = join.getDefaultOutgoingTransition();
            if (out != null) {
                queue.addFirst(new Step(node, requireActivity(tree.definition(), out.destinationName())));
            }
            return;
        }

        node.setStatus(ExecutionStatus.AWAITING_JOIN);
        node.setCurrentActivityName(join.getName());
        tryFireJoin(tree, parent, join, node, queue);
    }

    private boolean tryFireJoin(
            ExecutionTree tree,
            ExecutionTree.MutableNode parent,
            ActivityDefinition join,
            ExecutionTree.MutableNode variableContext,
            Deque<Step> queue
    ) {
        List<ExecutionTree.MutableNode> siblings = parent.mutableChildren();
        int arrivedCount = 0;
        for (ExecutionTree.MutableNode sibling : siblings) {
            if (sibling.getStatus() == ExecutionStatus.AWAITING_JOIN
                    && join.getName().equals(sibling.getCurrentActivityName())) {
                arrivedCount++;
            }
        }

        int required = resolveJoinMultiplicity(join, siblings.size(), variableContext);
        if (arrivedCount < required) {
            return false;
        }

        // All required branches have arrived: end & detach all child branches of this fork parent
        for (ExecutionTree.MutableNode sibling : new ArrayList<>(siblings)) {
            endSubtree(sibling, ExecutionStatus.COMPLETED, null);
            tree.removeSubtree(sibling);
        }

        parent.setStatus(parent.isProcessInstance() ? ExecutionStatus.RUNNING : ExecutionStatus.RUNNING_BRANCH);
        parent.setCurrentActivityName(join.getName());

        TransitionDefinition out = join.getDefaultOutgoingTransition();
        if (out != null) {
            queue.addFirst(new Step(parent, requireActivity(tree.definition(), out.destinationName())));
        }
        return true;
    }

    private int resolveJoinMultiplicity(
            ActivityDefinition join,
            int liveSiblingCount,
            ExecutionTree.MutableNode variableContext
    ) {
        String expr = join.getJoinMultiplicityExpression();
        if (expr == null || expr.isBlank()) {
            return liveSiblingCount;
        }
        Object evaluated = expressionEvaluator.evaluate(expr, variableContext::getVariable);
        int multiplicity;
        if (evaluated instanceof Number n) {
            multiplicity = n.intValue();
        } else if (evaluated != null) {
            try {
                multiplicity = Integer.parseInt(String.valueOf(evaluated).trim());
            } catch (NumberFormatException e) {
                throw new JaybeeException(
                        "Invalid join multiplicity '" + evaluated + "' on join '" + join.getName() + "'", e
                );
            }
        } else {
            throw new JaybeeException("Join multiplicity evaluated to null on join '" + join.getName() + "'");
        }
        if (multiplicity < 1) {
            throw new JaybeeException(
                    "Join multiplicity must be >= 1 on join '" + join.getName() + "', got " + multiplicity
            );
        }
        return multiplicity;
    }

    private void executeEnd(
            ExecutionTree tree,
            ExecutionTree.MutableNode node,
            ActivityDefinition end,
            Deque<Step> queue
    ) {
        ExecutionStatus targetStatus = end.getEndStatus();
        if (targetStatus == null || !targetStatus.isTerminal()) {
            targetStatus = switch (end.getType()) {
                case END_CANCEL -> ExecutionStatus.CANCELLED;
                case END_ERROR -> ExecutionStatus.FAILED;
                default -> ExecutionStatus.COMPLETED;
            };
        }
        String outcome = end.getEndOutcome();

        if (end.getEndScope() == EndScope.PROCESS_INSTANCE) {
            endSubtree(tree.root(), targetStatus, outcome);
            tree.root().setCurrentActivityName(end.getName());
        } else {
            endExecutionBranch(tree, node, targetStatus, outcome, queue);
        }
    }

    private void endExecutionBranch(
            ExecutionTree tree,
            ExecutionTree.MutableNode node,
            ExecutionStatus targetStatus,
            String outcome,
            Deque<Step> queue
    ) {
        ExecutionTree.MutableNode parent = node.getParent();
        endSubtree(node, targetStatus, outcome);
        if (parent == null) {
            return;
        }
        tree.removeSubtree(node);
        if (parent.mutableChildren().isEmpty()) {
            endExecutionBranch(tree, parent, targetStatus, outcome, queue);
            return;
        }
        // Re-check any sibling executions waiting at a join now that a sibling branch has ended
        Set<String> waitingJoinNames = new LinkedHashSet<>();
        for (ExecutionTree.MutableNode sibling : parent.mutableChildren()) {
            if (sibling.getStatus() == ExecutionStatus.AWAITING_JOIN && sibling.getCurrentActivityName() != null) {
                waitingJoinNames.add(sibling.getCurrentActivityName());
            }
        }
        for (String joinName : waitingJoinNames) {
            ActivityDefinition joinDef = tree.definition().getActivity(joinName);
            if (joinDef != null && joinDef.getType() == ActivityType.JOIN) {
                if (tryFireJoin(tree, parent, joinDef, parent, queue)) {
                    break;
                }
            }
        }
    }

    private void endSubtree(ExecutionTree.MutableNode node, ExecutionStatus targetStatus, String outcome) {
        node.setStatus(targetStatus);
        node.setTerminalOutcome(outcome);
        for (ExecutionTree.MutableNode child : node.mutableChildren()) {
            endSubtree(child, targetStatus, outcome);
        }
    }

    private TransitionDefinition resolveStateTransition(ActivityDefinition state, String signalName) {
        if (signalName != null) {
            return state.getOutgoingTransition(signalName);
        }
        List<TransitionDefinition> outgoing = state.getOutgoingTransitions();
        if (outgoing.isEmpty()) {
            return null;
        }
        if (outgoing.size() == 1) {
            return outgoing.get(0);
        }
        for (TransitionDefinition t : outgoing) {
            if (t.name() == null || t.name().isBlank()) {
                return t;
            }
        }
        return outgoing.get(0);
    }

    private ActivityDefinition requireActivity(ProcessDefinition definition, String name) {
        ActivityDefinition activity = definition.getActivity(name);
        if (activity == null) {
            throw new JaybeeException(
                    "Activity '" + name + "' not found in process definition " + definition.getId()
            );
        }
        return activity;
    }
}
