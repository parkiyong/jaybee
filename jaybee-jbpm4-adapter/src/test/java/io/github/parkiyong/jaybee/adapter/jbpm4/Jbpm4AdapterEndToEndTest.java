package io.github.parkiyong.jaybee.adapter.jbpm4;

import org.jbpm.api.Deployment;
import org.jbpm.api.DeploymentQuery;
import org.jbpm.api.Execution;
import org.jbpm.api.ExecutionService;
import org.jbpm.api.JbpmException;
import org.jbpm.api.ProcessDefinition;
import org.jbpm.api.ProcessDefinitionQuery;
import org.jbpm.api.ProcessEngine;
import org.jbpm.api.ProcessInstance;
import org.jbpm.api.ProcessInstanceQuery;
import org.jbpm.api.RepositoryService;
import org.jbpm.api.jpdl.DecisionHandler;
import org.jbpm.api.model.ActivityCoordinates;
import org.jbpm.api.model.OpenExecution;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end verification tests written strictly against unmodified {@code org.jbpm.api.*}.
 */
class Jbpm4AdapterEndToEndTest {

    public static class LegacyDecisionHandler implements DecisionHandler {
        private static final long serialVersionUID = 1L;

        private int limit;
        private String prefix;

        public void setPrefix(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public String decide(OpenExecution execution) {
            assertEquals("evaluate", execution.getActivity().getName());
            assertEquals("decision", execution.getActivity().getType());
            int total = ((Number) execution.getVariable("total")).intValue();
            execution.setVariable("handlerTag", prefix + ":" + limit);
            return total > limit ? "escalate" : "standard";
        }
    }

    @Test
    void executesJpdlWorkflowWithDecisionHandlerForkJoinAndVariables() {
        String jpdl = """
                <process name="Purchase Approval" key="purchase" xmlns="http://jbpm.org/4.4/jpdl">
                  <start name="start" g="10,10,48,48">
                    <transition to="evaluate"/>
                  </start>
                  <decision name="evaluate" g="90,10,48,48">
                    <handler class="%s">
                      <field name="limit"><int value="500"/></field>
                      <property name="prefix"><string value="RULE"/></property>
                    </handler>
                    <transition name="escalate" to="parallelReview"/>
                    <transition name="standard" to="autoApprove"/>
                  </decision>
                  <fork name="parallelReview" g="180,10,48,48">
                    <transition name="finance" to="financeCheck"/>
                    <transition name="legal" to="legalCheck"/>
                  </fork>
                  <state name="financeCheck" g="260,0,100,40">
                    <transition name="ok" to="joinReview"/>
                    <transition name="abort" to="cancelled"/>
                  </state>
                  <state name="legalCheck" g="260,60,100,40">
                    <transition name="ok" to="joinReview"/>
                  </state>
                  <join name="joinReview" g="400,30,48,48">
                    <transition to="completed"/>
                  </join>
                  <state name="autoApprove" g="180,120,100,40">
                    <transition to="completed"/>
                  </state>
                  <end name="completed" g="500,30,48,48"/>
                  <end-cancel name="cancelled" g="400,120,48,48"/>
                </process>
                """.formatted(LegacyDecisionHandler.class.getName());

        ProcessEngine engine = JaybeeJbpm4Adapter.createDefaultProcessEngine();
        try {
            RepositoryService repo = engine.getRepositoryService();
            ExecutionService exec = engine.getExecutionService();

            String depId = repo.createDeployment()
                    .setName("purchase-v1")
                    .setTimestamp(1000L)
                    .addResourceFromString("purchase.jpdl.xml", jpdl)
                    .deploy();

            Deployment dep = repo.createDeploymentQuery()
                    .deploymentId(depId)
                    .orderAsc(DeploymentQuery.PROPERTY_TIMESTAMP)
                    .uniqueResult();
            assertNotNull(dep);
            assertEquals("purchase-v1", dep.getName());
            assertEquals(Deployment.STATE_ACTIVE, dep.getState());

            ProcessDefinition pd = repo.createProcessDefinitionQuery()
                    .processDefinitionKey("purchase")
                    .orderDesc(ProcessDefinitionQuery.PROPERTY_VERSION)
                    .uniqueResult();
            assertNotNull(pd);
            assertEquals("purchase-1", pd.getId());
            assertEquals(List.of("start"), repo.getStartActivityNames(pd.getId()));

            ActivityCoordinates coords = repo.getActivityCoordinates(pd.getId(), "financeCheck");
            assertNotNull(coords);
            assertEquals(260, coords.getX());
            assertEquals(100, coords.getWidth());

            // High amount -> escalates into parallel fork
            ProcessInstance pi = exec.startProcessInstanceByKey("purchase", Map.of("total", 1200), "PO-100");
            assertEquals("purchase.PO-100", pi.getId());
            assertEquals("PO-100", pi.getKey());
            assertEquals(Set.of("financeCheck", "legalCheck"), pi.findActiveActivityNames());
            assertEquals("RULE:500", exec.getVariable(pi.getId(), "handlerTag"));

            ProcessInstance queried = exec.createProcessInstanceQuery()
                    .processInstanceKey("PO-100")
                    .orderAsc(ProcessInstanceQuery.PROPERTY_KEY)
                    .uniqueResult();
            assertNotNull(queried);
            assertEquals(pi.getId(), queried.getId());

            Execution finance = pi.findActiveExecutionIn("financeCheck");
            Execution legal = pi.findActiveExecutionIn("legalCheck");
            assertNotNull(finance);
            assertNotNull(legal);

            pi = exec.signalExecutionById(finance.getId(), "ok", Map.of("financeApproved", true));
            assertEquals(Set.of("legalCheck"), pi.findActiveActivityNames());
            assertEquals(Boolean.TRUE, exec.getVariable(pi.getId(), "financeApproved"));

            pi = exec.signalExecutionById(legal.getId(), "ok");
            assertTrue(pi.isEnded());
            assertEquals(Execution.STATE_ENDED, pi.getState());
            assertNull(exec.findProcessInstanceById(pi.getId()));

            // Cancellation path via end-cancel
            ProcessInstance pi2 = exec.startProcessInstanceByKey("purchase", Map.of("total", 900), "PO-101");
            Execution finance2 = pi2.findActiveExecutionIn("financeCheck");
            pi2 = exec.signalExecutionById(finance2.getId(), "abort");
            assertTrue(pi2.isEnded());
            assertEquals("cancel", pi2.getState());

            // Standard path (<= 500)
            ProcessInstance pi3 = exec.startProcessInstanceByKey("purchase", Map.of("total", 200));
            assertTrue(pi3.isActive("autoApprove"));
            assertThrows(JbpmException.class, () -> repo.deleteDeployment(depId));
            repo.deleteDeploymentCascade(depId);
            assertEquals(0L, repo.createProcessDefinitionQuery().count());
        } finally {
            engine.close();
        }
    }

    @Test
    void supportsDecisionExprAndConditionAndThrowsOnDeferredServices() {
        String jpdl = """
                <process name="DecisionModes">
                  <start><transition to="d1"/></start>
                  <decision name="d1" expr="#{route}">
                    <transition name="cond" to="d2"/>
                    <transition name="err" to="failed"/>
                  </decision>
                  <decision name="d2">
                    <transition name="big" to="done">
                      <condition expr="#{score >= 80}"/>
                    </transition>
                    <transition name="fallback" to="failed"/>
                  </decision>
                  <end name="done"/>
                  <end-error name="failed"/>
                </process>
                """;

        ProcessEngine engine = JaybeeJbpm4Adapter.createDefaultProcessEngine();
        try {
            engine.getRepositoryService().createDeployment()
                    .addResourceFromString("modes.jpdl.xml", jpdl)
                    .deploy();

            ProcessInstance ok = engine.getExecutionService()
                    .startProcessInstanceByKey("DecisionModes", Map.of("route", "cond", "score", 95));
            assertTrue(ok.isEnded());
            assertEquals(Execution.STATE_ENDED, ok.getState());

            ProcessInstance fail = engine.getExecutionService()
                    .startProcessInstanceByKey("DecisionModes", Map.of("route", "cond", "score", 40));
            assertTrue(fail.isEnded());
            assertEquals("error", fail.getState());

            assertThrows(UnsupportedOperationException.class, engine::getHistoryService);
            assertThrows(UnsupportedOperationException.class, engine::getTaskService);
            assertThrows(UnsupportedOperationException.class, engine::getManagementService);
            assertThrows(UnsupportedOperationException.class, engine::getIdentityService);
        } finally {
            engine.close();
        }
    }

    @Test
    void preservesCustomEndStateAndEndProcessInstanceThroughAdapter() {
        String jpdl = """
                <process name="CustomEnd">
                  <start><transition to="wait"/></start>
                  <state name="wait">
                    <transition name="finish" to="done"/>
                  </state>
                  <end name="done" state="approved-custom"/>
                </process>
                """;

        ProcessEngine engine = JaybeeJbpm4Adapter.createDefaultProcessEngine();
        try {
            engine.getRepositoryService().createDeployment()
                    .addResourceFromString("custom.jpdl.xml", jpdl)
                    .deploy();

            ProcessInstance pi1 = engine.getExecutionService().startProcessInstanceByKey("CustomEnd");
            pi1 = engine.getExecutionService().signalExecutionById(pi1.getId(), "finish");
            assertTrue(pi1.isEnded());
            assertEquals("approved-custom", pi1.getState());

            ProcessInstance pi2 = engine.getExecutionService().startProcessInstanceByKey("CustomEnd");
            engine.getExecutionService().endProcessInstance(pi2.getId(), "terminated-by-admin");
            assertNull(engine.getExecutionService().findProcessInstanceById(pi2.getId()));
        } finally {
            engine.close();
        }
    }
}
