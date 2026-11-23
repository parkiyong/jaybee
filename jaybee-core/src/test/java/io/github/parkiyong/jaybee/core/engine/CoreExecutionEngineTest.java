package io.github.parkiyong.jaybee.core.engine;

import io.github.parkiyong.jaybee.api.DecisionHandler;
import io.github.parkiyong.jaybee.api.JaybeeEngine;
import io.github.parkiyong.jaybee.api.ProcessDefinitionQuery;
import io.github.parkiyong.jaybee.api.RepositoryService;
import io.github.parkiyong.jaybee.api.RuntimeService;
import io.github.parkiyong.jaybee.api.SignalRequest;
import io.github.parkiyong.jaybee.api.StartProcessRequest;
import io.github.parkiyong.jaybee.api.exception.JaybeeException;
import io.github.parkiyong.jaybee.api.exception.OptimisticLockException;
import io.github.parkiyong.jaybee.api.model.ActivityDefinition;
import io.github.parkiyong.jaybee.api.model.ActivityType;
import io.github.parkiyong.jaybee.api.model.DeploymentStatus;
import io.github.parkiyong.jaybee.api.model.EndScope;
import io.github.parkiyong.jaybee.api.model.Execution;
import io.github.parkiyong.jaybee.api.model.ExecutionStatus;
import io.github.parkiyong.jaybee.api.model.HandlerDescriptor;
import io.github.parkiyong.jaybee.api.model.HandlerField;
import io.github.parkiyong.jaybee.api.model.MutableExecution;
import io.github.parkiyong.jaybee.api.model.ProcessDefinition;
import io.github.parkiyong.jaybee.api.model.ProcessInstance;
import io.github.parkiyong.jaybee.api.model.TransitionDefinition;
import io.github.parkiyong.jaybee.api.query.PageRequest;
import io.github.parkiyong.jaybee.api.query.SortOrder;
import io.github.parkiyong.jaybee.api.spi.ExecutionNodeData;
import io.github.parkiyong.jaybee.api.spi.ParsedProcessDefinition;
import io.github.parkiyong.jaybee.api.spi.ProcessDefinitionParser;
import io.github.parkiyong.jaybee.core.JaybeeEngineBuilder;
import io.github.parkiyong.jaybee.core.store.InMemoryProcessStore;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

class CoreExecutionEngineTest {

    public static class ThresholdDecisionHandler implements DecisionHandler {
        private int threshold;
        private String highTransition;

        public void setHighTransition(String highTransition) {
            this.highTransition = highTransition;
        }

        @Override
        public String decide(MutableExecution execution) {
            int amount = ((Number) execution.getVariable("amount")).intValue();
            execution.setVariable("evaluatedThreshold", threshold);
            return amount >= threshold ? highTransition : "low";
        }
    }

    private JaybeeEngine newEngineWith(Map<String, ParsedProcessDefinition> definitions, InMemoryProcessStore store) {
        ProcessDefinitionParser stubParser = new ProcessDefinitionParser() {
            @Override
            public boolean supports(String resourceName) {
                return definitions.containsKey(resourceName);
            }

            @Override
            public ParsedProcessDefinition parse(String resourceName, byte[] content) {
                return definitions.get(resourceName);
            }
        };
        JaybeeEngineBuilder builder = JaybeeEngineBuilder.create().registerParser(stubParser);
        if (store != null) {
            builder.withStore(store);
        }
        return builder.build();
    }

    @Test
    void runsLinearStateWorkflowWithSignalsAndVariables() {
        ParsedProcessDefinition def = new ParsedProcessDefinition(
                "linear", "Linear", null, "desc", null,
                List.of(
                        ActivityDefinition.builder("start", ActivityType.START)
                                .outgoingTransitions(List.of(new TransitionDefinition(null, "start", "review", null)))
                                .build(),
                        ActivityDefinition.builder("review", ActivityType.STATE)
                                .outgoingTransitions(List.of(
                                        new TransitionDefinition("approve", "review", "approved", null),
                                        new TransitionDefinition("reject", "review", "rejected", null)
                                ))
                                .build(),
                        ActivityDefinition.builder("approved", ActivityType.END).build(),
                        ActivityDefinition.builder("rejected", ActivityType.END_CANCEL).build()
                )
        );

        try (JaybeeEngine engine = newEngineWith(Map.of("linear.test", def), null)) {
            engine.repository().newDeployment()
                    .addStringResource("linear.test", "dummy")
                    .deploy();

            RuntimeService runtime = engine.runtime();
            ProcessInstance pi = runtime.startProcess(
                    StartProcessRequest.byKey("linear")
                            .withBusinessKey("REQ-1")
                            .withVariable("requester", "kim")
            );
            assertEquals("linear.REQ-1", pi.getId());
            assertEquals("REQ-1", pi.getKey());
            assertTrue(pi.isActive("review"));
            assertFalse(pi.isEnded());
            assertEquals(Optional.of("kim"), runtime.readVariable(pi.getId(), "requester"));

            ProcessInstance ended = runtime.signal(
                    SignalRequest.forExecution(pi.getId())
                            .withTransition("reject")
                            .withVariable("reason", "incomplete")
            );
            assertTrue(ended.isEnded());
            assertEquals(ExecutionStatus.CANCELLED, ended.getStatus());
            assertTrue(runtime.findInstance(pi.getId()).isEmpty());
        }
    }

    @Test
    void supportsNativeDecisionHandlerWithFieldAndPropertyInjection() {
        ParsedProcessDefinition def = new ParsedProcessDefinition(
                "decHandler", "DecHandler", null, null, null,
                List.of(
                        ActivityDefinition.builder("start", ActivityType.START)
                                .outgoingTransitions(List.of(new TransitionDefinition(null, "start", "decide", null)))
                                .build(),
                        ActivityDefinition.builder("decide", ActivityType.DECISION)
                                .decisionHandler(new HandlerDescriptor(
                                        ThresholdDecisionHandler.class.getName(),
                                        null,
                                        List.of(
                                                new HandlerField("threshold", 100, false),
                                                new HandlerField("highTransition", "high", true)
                                        )
                                ))
                                .outgoingTransitions(List.of(
                                        new TransitionDefinition("high", "decide", "highWait", null),
                                        new TransitionDefinition("low", "decide", "lowWait", null)
                                ))
                                .build(),
                        ActivityDefinition.builder("highWait", ActivityType.STATE)
                                .outgoingTransitions(List.of(new TransitionDefinition(null, "highWait", "done", null)))
                                .build(),
                        ActivityDefinition.builder("lowWait", ActivityType.STATE)
                                .outgoingTransitions(List.of(new TransitionDefinition(null, "lowWait", "done", null)))
                                .build(),
                        ActivityDefinition.builder("done", ActivityType.END).build()
                )
        );

        try (JaybeeEngine engine = newEngineWith(Map.of("dec.test", def), null)) {
            engine.repository().newDeployment().addStringResource("dec.test", "x").deploy();
            RuntimeService runtime = engine.runtime();

            ProcessInstance high = runtime.startProcessByKey("decHandler", Map.of("amount", 150));
            assertTrue(high.isActive("highWait"));
            assertEquals(Optional.of(100), runtime.readVariable(high.getId(), "evaluatedThreshold"));

            ProcessInstance low = runtime.startProcessByKey("decHandler", Map.of("amount", 50));
            assertTrue(low.isActive("lowWait"));
        }
    }

    @Test
    void supportsNestedForkJoinMultiplicityAndHierarchicalVariables() {
        ParsedProcessDefinition def = new ParsedProcessDefinition(
                "nestedFork", "NestedFork", null, null, null,
                List.of(
                        ActivityDefinition.builder("start", ActivityType.START)
                                .outgoingTransitions(List.of(new TransitionDefinition(null, "start", "outerFork", null)))
                                .build(),
                        ActivityDefinition.builder("outerFork", ActivityType.FORK)
                                .outgoingTransitions(List.of(
                                        new TransitionDefinition("left", "outerFork", "innerFork", null),
                                        new TransitionDefinition("right", "outerFork", "rightWait", null)
                                ))
                                .build(),
                        ActivityDefinition.builder("innerFork", ActivityType.FORK)
                                .outgoingTransitions(List.of(
                                        new TransitionDefinition("a", "innerFork", "waitA", null),
                                        new TransitionDefinition("b", "innerFork", "waitB", null),
                                        new TransitionDefinition("c", "innerFork", "waitC", null)
                                ))
                                .build(),
                        ActivityDefinition.builder("waitA", ActivityType.STATE)
                                .outgoingTransitions(List.of(new TransitionDefinition(null, "waitA", "innerJoin", null)))
                                .build(),
                        ActivityDefinition.builder("waitB", ActivityType.STATE)
                                .outgoingTransitions(List.of(new TransitionDefinition(null, "waitB", "innerJoin", null)))
                                .build(),
                        ActivityDefinition.builder("waitC", ActivityType.STATE)
                                .outgoingTransitions(List.of(new TransitionDefinition(null, "waitC", "innerJoin", null)))
                                .build(),
                        ActivityDefinition.builder("innerJoin", ActivityType.JOIN)
                                .joinMultiplicityExpression("#{quorum}")
                                .outgoingTransitions(List.of(new TransitionDefinition(null, "innerJoin", "outerJoin", null)))
                                .build(),
                        ActivityDefinition.builder("rightWait", ActivityType.STATE)
                                .outgoingTransitions(List.of(new TransitionDefinition(null, "rightWait", "outerJoin", null)))
                                .build(),
                        ActivityDefinition.builder("outerJoin", ActivityType.JOIN)
                                .outgoingTransitions(List.of(new TransitionDefinition(null, "outerJoin", "afterJoin", null)))
                                .build(),
                        ActivityDefinition.builder("afterJoin", ActivityType.STATE)
                                .outgoingTransitions(List.of(new TransitionDefinition(null, "afterJoin", "end", null)))
                                .build(),
                        ActivityDefinition.builder("end", ActivityType.END).build()
                )
        );

        try (JaybeeEngine engine = newEngineWith(Map.of("nf.test", def), null)) {
            engine.repository().newDeployment().addStringResource("nf.test", "x").deploy();
            RuntimeService runtime = engine.runtime();

            ProcessInstance pi = runtime.startProcessByKey("nestedFork", Map.of("quorum", 2, "shared", "rootVal"));
            assertEquals(Set.of("waitA", "waitB", "waitC", "rightWait"), pi.findActiveActivityNames());

            Execution execA = pi.findActiveExecutionIn("waitA");
            Execution execB = pi.findActiveExecutionIn("waitB");
            Execution execRight = pi.findActiveExecutionIn("rightWait");

            // Hierarchical variable scoping: child local shadows root without overwriting root
            runtime.declareLocalVariable(execA.getId(), "shared", "localA");
            assertEquals(Optional.of("localA"), runtime.readVariable(execA.getId(), "shared"));
            assertEquals(Optional.of("rootVal"), runtime.readVariable(execRight.getId(), "shared"));
            assertEquals(Optional.of("rootVal"), runtime.readVariable(pi.getId(), "shared"));

            // putVariable on child without local key writes to root
            runtime.putVariable(execRight.getId(), "fromRight", 42);
            assertEquals(Optional.of(42), runtime.readVariable(pi.getId(), "fromRight"));

            // Signal 2 of 3 inner branches (quorum = 2) -> innerJoin fires and cancels waitC
            pi = runtime.signal(execA.getId());
            assertEquals(Set.of("waitB", "waitC", "rightWait"), pi.findActiveActivityNames());

            pi = runtime.signal(execB.getId());
            // Left branch is now waiting at outerJoin; only rightWait remains active
            assertEquals(Set.of("rightWait"), pi.findActiveActivityNames());

            // Signal rightWait -> outerJoin fires -> afterJoin
            pi = runtime.signal(execRight.getId());
            assertEquals(Set.of("afterJoin"), pi.findActiveActivityNames());
            assertTrue(pi.getExecutions().isEmpty());

            // Final signal ends process instance
            pi = runtime.signal(pi.getId());
            assertTrue(pi.isEnded());
            assertEquals(ExecutionStatus.COMPLETED, pi.getStatus());
        }
    }

    @Test
    void supportsExecutionScopedEndInBothArrivalOrders() {
        ParsedProcessDefinition def = new ParsedProcessDefinition(
                "endScope", "EndScope", null, null, null,
                List.of(
                        ActivityDefinition.builder("start", ActivityType.START)
                                .outgoingTransitions(List.of(new TransitionDefinition(null, "start", "fork", null)))
                                .build(),
                        ActivityDefinition.builder("fork", ActivityType.FORK)
                                .outgoingTransitions(List.of(
                                        new TransitionDefinition("b1", "fork", "s1", null),
                                        new TransitionDefinition("b2", "fork", "s2", null)
                                ))
                                .build(),
                        ActivityDefinition.builder("s1", ActivityType.STATE)
                                .outgoingTransitions(List.of(new TransitionDefinition(null, "s1", "join", null)))
                                .build(),
                        ActivityDefinition.builder("s2", ActivityType.STATE)
                                .outgoingTransitions(List.of(new TransitionDefinition(null, "s2", "branchEnd", null)))
                                .build(),
                        ActivityDefinition.builder("branchEnd", ActivityType.END)
                                .endScope(EndScope.EXECUTION)
                                .build(),
                        ActivityDefinition.builder("join", ActivityType.JOIN)
                                .outgoingTransitions(List.of(new TransitionDefinition(null, "join", "finalWait", null)))
                                .build(),
                        ActivityDefinition.builder("finalWait", ActivityType.STATE)
                                .outgoingTransitions(List.of(new TransitionDefinition(null, "finalWait", "done", null)))
                                .build(),
                        ActivityDefinition.builder("done", ActivityType.END).build()
                )
        );

        try (JaybeeEngine engine = newEngineWith(Map.of("es.test", def), null)) {
            engine.repository().newDeployment().addStringResource("es.test", "x").deploy();
            RuntimeService runtime = engine.runtime();

            // Order 1: s2 ends branch first, then s1 reaches join
            ProcessInstance pi1 = runtime.startProcessByKey("endScope");
            pi1 = runtime.signal(pi1.findActiveExecutionIn("s2").getId());
            assertEquals(Set.of("s1"), pi1.findActiveActivityNames());
            pi1 = runtime.signal(pi1.findActiveExecutionIn("s1").getId());
            assertEquals(Set.of("finalWait"), pi1.findActiveActivityNames());

            // Order 2: s1 arrives at join first, then s2 ends its branch -> triggers waiting join
            ProcessInstance pi2 = runtime.startProcessByKey("endScope");
            pi2 = runtime.signal(pi2.findActiveExecutionIn("s1").getId());
            assertEquals(Set.of("s2"), pi2.findActiveActivityNames());
            pi2 = runtime.signal(pi2.findActiveExecutionIn("s2").getId());
            assertEquals(Set.of("finalWait"), pi2.findActiveActivityNames());
        }
    }

    @Test
    void handlesConcurrentSignalsAcrossForkBranchesSafely() throws Exception {
        ParsedProcessDefinition def = new ParsedProcessDefinition(
                "concurrent", "Concurrent", null, null, null,
                List.of(
                        ActivityDefinition.builder("start", ActivityType.START)
                                .outgoingTransitions(List.of(new TransitionDefinition(null, "start", "fork", null)))
                                .build(),
                        ActivityDefinition.builder("fork", ActivityType.FORK)
                                .outgoingTransitions(List.of(
                                        new TransitionDefinition("b1", "fork", "s1", null),
                                        new TransitionDefinition("b2", "fork", "s2", null),
                                        new TransitionDefinition("b3", "fork", "s3", null),
                                        new TransitionDefinition("b4", "fork", "s4", null)
                                ))
                                .build(),
                        ActivityDefinition.builder("s1", ActivityType.STATE)
                                .outgoingTransitions(List.of(new TransitionDefinition(null, "s1", "join", null))).build(),
                        ActivityDefinition.builder("s2", ActivityType.STATE)
                                .outgoingTransitions(List.of(new TransitionDefinition(null, "s2", "join", null))).build(),
                        ActivityDefinition.builder("s3", ActivityType.STATE)
                                .outgoingTransitions(List.of(new TransitionDefinition(null, "s3", "join", null))).build(),
                        ActivityDefinition.builder("s4", ActivityType.STATE)
                                .outgoingTransitions(List.of(new TransitionDefinition(null, "s4", "join", null))).build(),
                        ActivityDefinition.builder("join", ActivityType.JOIN)
                                .outgoingTransitions(List.of(new TransitionDefinition(null, "join", "end", null))).build(),
                        ActivityDefinition.builder("end", ActivityType.END).build()
                )
        );

        ExecutorService pool = Executors.newFixedThreadPool(4);
        try (JaybeeEngine engine = newEngineWith(Map.of("c.test", def), null)) {
            engine.repository().newDeployment().addStringResource("c.test", "x").deploy();
            RuntimeService runtime = engine.runtime();

            for (int round = 0; round < 20; round++) {
                ProcessInstance pi = runtime.startProcessByKey("concurrent");
                List<String> branchIds = List.of(
                        pi.findActiveExecutionIn("s1").getId(),
                        pi.findActiveExecutionIn("s2").getId(),
                        pi.findActiveExecutionIn("s3").getId(),
                        pi.findActiveExecutionIn("s4").getId()
                );

                CountDownLatch ready = new CountDownLatch(4);
                CountDownLatch go = new CountDownLatch(1);
                List<Callable<ProcessInstance>> tasks = branchIds.stream()
                        .<Callable<ProcessInstance>>map(id -> () -> {
                            ready.countDown();
                            go.await();
                            return runtime.signal(id);
                        })
                        .toList();

                List<Future<ProcessInstance>> futures = tasks.stream().map(pool::submit).toList();
                ready.await();
                go.countDown();

                int endedCount = 0;
                for (Future<ProcessInstance> f : futures) {
                    if (f.get().isEnded()) {
                        endedCount++;
                    }
                }
                assertEquals(1, endedCount, "Exactly the last thread to arrive at join should end the instance");
                assertTrue(runtime.findInstance(pi.getId()).isEmpty());
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void enforcesOptimisticLockingAndRepositoryLifecycle() {
        InMemoryProcessStore store = new InMemoryProcessStore();
        ParsedProcessDefinition def = new ParsedProcessDefinition(
                "repo", "Repo Process", null, null, null,
                List.of(
                        ActivityDefinition.builder("start", ActivityType.START)
                                .outgoingTransitions(List.of(new TransitionDefinition(null, "start", "wait", null)))
                                .build(),
                        ActivityDefinition.builder("wait", ActivityType.STATE)
                                .outgoingTransitions(List.of(new TransitionDefinition(null, "wait", "end", null)))
                                .build(),
                        ActivityDefinition.builder("end", ActivityType.END).build()
                )
        );

        try (JaybeeEngine engine = newEngineWith(Map.of("r.test", def), store)) {
            RepositoryService repo = engine.repository();
            RuntimeService runtime = engine.runtime();

            String dep1 = repo.newDeployment()
                    .named("v1")
                    .deployedAt(Instant.ofEpochMilli(100L))
                    .addStringResource("r.test", "1")
                    .deploy();
            String dep2 = repo.newDeployment()
                    .named("v2")
                    .deployedAt(Instant.ofEpochMilli(200L))
                    .addStringResource("r.test", "2")
                    .deploy();

            List<ProcessDefinition> defs = repo.definitions()
                    .withNameMatching("Repo%")
                    .sortBy(ProcessDefinitionQuery.SortField.VERSION, SortOrder.DESC)
                    .paginate(PageRequest.of(0, 10))
                    .fetch();
            assertEquals(2, defs.size());
            assertEquals("repo-2", defs.get(0).getId());
            assertEquals("repo-1", defs.get(1).getId());

            ProcessInstance pi = runtime.startProcessByKey("repo");
            assertEquals("repo-2", pi.getProcessDefinitionId());

            // Optimistic lock verification directly against store
            List<ExecutionNodeData> snapshot = store.findExecutionTreeByProcessInstanceId(pi.getId()).orElseThrow();
            assertThrows(OptimisticLockException.class,
                    () -> store.saveExecutionTree(pi.getId(), snapshot, 999L));

            // Suspend blocks starting new instances and signalling existing ones
            repo.suspendDeployment(dep2);
            assertEquals(1L, repo.deployments().withStatus(DeploymentStatus.SUSPENDED).count());
            assertThrows(JaybeeException.class, () -> runtime.startProcess(StartProcessRequest.byId("repo-2")));
            assertThrows(JaybeeException.class, () -> runtime.signal(pi.getId()));
            assertEquals(1L, runtime.instances().withSuspended(true).count());

            repo.resumeDeployment(dep2);
            assertEquals(1L, runtime.instances().withSuspended(false).count());

            // Non-cascade delete fails while active instance exists
            assertThrows(JaybeeException.class, () -> repo.removeDeployment(dep2, false));
            repo.removeDeployment(dep2, true);
            assertTrue(runtime.findInstance(pi.getId()).isEmpty());
            repo.removeDeployment(dep1, false);
            assertEquals(0L, repo.deployments().count());
        }
    }
}
