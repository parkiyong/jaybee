package io.github.parkiyong.jaybee.adapter.jbpm4;

import io.github.parkiyong.jaybee.api.RuntimeService;
import io.github.parkiyong.jaybee.api.SignalRequest;
import io.github.parkiyong.jaybee.api.StartProcessRequest;
import io.github.parkiyong.jaybee.api.exception.JaybeeException;
import io.github.parkiyong.jaybee.api.model.ExecutionStatus;
import io.github.parkiyong.jaybee.api.query.PageRequest;
import io.github.parkiyong.jaybee.api.query.SortOrder;
import org.jbpm.api.Execution;
import org.jbpm.api.ExecutionService;
import org.jbpm.api.JbpmException;
import org.jbpm.api.ProcessInstance;
import org.jbpm.api.ProcessInstanceQuery;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * {@link ExecutionService} adapter delegating to Jaybee's native {@link RuntimeService}.
 */
final class Jbpm4ExecutionService implements ExecutionService {

    private final RuntimeService delegate;

    Jbpm4ExecutionService(RuntimeService delegate) {
        this.delegate = delegate;
    }

    @Override
    public ProcessInstance startProcessInstanceById(String processDefinitionId) {
        return Jbpm4ModelAdapters.wrapProcessInstance(
                run(() -> delegate.startProcess(StartProcessRequest.byId(processDefinitionId)))
        );
    }

    @Override
    public ProcessInstance startProcessInstanceById(String processDefinitionId, String processInstanceKey) {
        return Jbpm4ModelAdapters.wrapProcessInstance(
                run(() -> delegate.startProcess(
                        StartProcessRequest.byId(processDefinitionId).withBusinessKey(processInstanceKey)
                ))
        );
    }

    @Override
    public ProcessInstance startProcessInstanceById(String processDefinitionId, Map<String, ?> variables) {
        return Jbpm4ModelAdapters.wrapProcessInstance(
                run(() -> delegate.startProcess(
                        StartProcessRequest.byId(processDefinitionId).withVariables(variables)
                ))
        );
    }

    @Override
    public ProcessInstance startProcessInstanceById(
            String processDefinitionId,
            Map<String, ?> variables,
            String processInstanceKey
    ) {
        return Jbpm4ModelAdapters.wrapProcessInstance(
                run(() -> delegate.startProcess(
                        StartProcessRequest.byId(processDefinitionId)
                                .withBusinessKey(processInstanceKey)
                                .withVariables(variables)
                ))
        );
    }

    @Override
    public ProcessInstance startProcessInstanceByKey(String processDefinitionKey) {
        return Jbpm4ModelAdapters.wrapProcessInstance(
                run(() -> delegate.startProcess(StartProcessRequest.byKey(processDefinitionKey)))
        );
    }

    @Override
    public ProcessInstance startProcessInstanceByKey(String processDefinitionKey, String processInstanceKey) {
        return Jbpm4ModelAdapters.wrapProcessInstance(
                run(() -> delegate.startProcess(
                        StartProcessRequest.byKey(processDefinitionKey).withBusinessKey(processInstanceKey)
                ))
        );
    }

    @Override
    public ProcessInstance startProcessInstanceByKey(String processDefinitionKey, Map<String, ?> variables) {
        return Jbpm4ModelAdapters.wrapProcessInstance(
                run(() -> delegate.startProcess(
                        StartProcessRequest.byKey(processDefinitionKey).withVariables(variables)
                ))
        );
    }

    @Override
    public ProcessInstance startProcessInstanceByKey(
            String processDefinitionKey,
            Map<String, ?> variables,
            String processInstanceKey
    ) {
        return Jbpm4ModelAdapters.wrapProcessInstance(
                run(() -> delegate.startProcess(
                        StartProcessRequest.byKey(processDefinitionKey)
                                .withBusinessKey(processInstanceKey)
                                .withVariables(variables)
                ))
        );
    }

    @Override
    public ProcessInstance signalExecutionById(String executionId) {
        return Jbpm4ModelAdapters.wrapProcessInstance(
                run(() -> delegate.signal(SignalRequest.forExecution(executionId)))
        );
    }

    @Override
    public ProcessInstance signalExecutionById(String executionId, String signalName) {
        return Jbpm4ModelAdapters.wrapProcessInstance(
                run(() -> delegate.signal(SignalRequest.forExecution(executionId).withTransition(signalName)))
        );
    }

    @Override
    public ProcessInstance signalExecutionById(String executionId, Map<String, ?> parameters) {
        return Jbpm4ModelAdapters.wrapProcessInstance(
                run(() -> delegate.signal(SignalRequest.forExecution(executionId).withPayload(parameters)))
        );
    }

    @Override
    public ProcessInstance signalExecutionById(String executionId, String signalName, Map<String, ?> parameters) {
        return Jbpm4ModelAdapters.wrapProcessInstance(
                run(() -> delegate.signal(
                        SignalRequest.forExecution(executionId)
                                .withTransition(signalName)
                                .withPayload(parameters)
                ))
        );
    }

    @Override
    public ProcessInstance findProcessInstanceById(String processInstanceId) {
        return Jbpm4ModelAdapters.wrapProcessInstance(
                run(() -> delegate.findInstance(processInstanceId).orElse(null))
        );
    }

    @Override
    public Execution findExecutionById(String executionId) {
        return Jbpm4ModelAdapters.wrapExecution(
                run(() -> delegate.findExecution(executionId).orElse(null))
        );
    }

    @Override
    public ProcessInstanceQuery createProcessInstanceQuery() {
        return new Jbpm4ProcessInstanceQuery(delegate.instances());
    }

    @Override
    public Object getVariable(String executionId, String variableName) {
        return run(() -> delegate.readVariable(executionId, variableName).orElse(null));
    }

    @Override
    public Set<String> getVariableNames(String executionId) {
        return run(() -> delegate.listVariableNames(executionId));
    }

    @Override
    public Map<String, Object> getVariables(String executionId, Set<String> variableNames) {
        return run(() -> delegate.readVariables(executionId, variableNames));
    }

    @Override
    public void setVariable(String executionId, String name, Object value) {
        run(() -> {
            delegate.putVariable(executionId, name, value);
            return null;
        });
    }

    @Override
    public void setVariables(String executionId, Map<String, ?> variables) {
        run(() -> {
            delegate.putVariables(executionId, variables);
            return null;
        });
    }

    @Override
    public void createVariable(String executionId, String name, Object value, boolean historyEnabled) {
        run(() -> {
            delegate.declareLocalVariable(executionId, name, value);
            return null;
        });
    }

    @Override
    public void createVariables(String executionId, Map<String, ?> variables, boolean historyEnabled) {
        run(() -> {
            delegate.declareLocalVariables(executionId, variables);
            return null;
        });
    }

    @Override
    public void endProcessInstance(String processInstanceId, String state) {
        run(() -> {
            Jbpm4ModelAdapters.MappedStatus mapped = Jbpm4ModelAdapters.fromJbpmTerminalState(state);
            ExecutionStatus status = mapped.status().isTerminal() ? mapped.status() : ExecutionStatus.COMPLETED;
            delegate.terminateInstance(processInstanceId, status, mapped.outcome());
            return null;
        });
    }

    @Override
    public void deleteProcessInstance(String processInstanceId) {
        run(() -> {
            delegate.removeInstance(processInstanceId, false);
            return null;
        });
    }

    @Override
    public void deleteProcessInstanceCascade(String processInstanceId) {
        run(() -> {
            delegate.removeInstance(processInstanceId, true);
            return null;
        });
    }

    private static <T> T run(Supplier<T> action) {
        try {
            return action.get();
        } catch (JaybeeException e) {
            throw new JbpmException(e.getMessage(), e);
        }
    }

    private static final class Jbpm4ProcessInstanceQuery implements ProcessInstanceQuery {
        private final io.github.parkiyong.jaybee.api.ProcessInstanceQuery delegate;

        Jbpm4ProcessInstanceQuery(io.github.parkiyong.jaybee.api.ProcessInstanceQuery delegate) {
            this.delegate = delegate;
        }

        @Override
        public ProcessInstanceQuery processInstanceId(String processInstanceId) {
            delegate.withId(processInstanceId);
            return this;
        }

        @Override
        public ProcessInstanceQuery processInstanceKey(String processInstanceKey) {
            delegate.withBusinessKey(processInstanceKey);
            return this;
        }

        @Override
        public ProcessInstanceQuery processDefinitionId(String processDefinitionId) {
            delegate.withDefinitionId(processDefinitionId);
            return this;
        }

        @Override
        public ProcessInstanceQuery suspended() {
            delegate.withSuspended(true);
            return this;
        }

        @Override
        public ProcessInstanceQuery notSuspended() {
            delegate.withSuspended(false);
            return this;
        }

        @Override
        public ProcessInstanceQuery orderAsc(String property) {
            delegate.sortBy(mapSortField(property), SortOrder.ASC);
            return this;
        }

        @Override
        public ProcessInstanceQuery orderDesc(String property) {
            delegate.sortBy(mapSortField(property), SortOrder.DESC);
            return this;
        }

        @Override
        public ProcessInstanceQuery page(int firstResult, int maxResults) {
            delegate.paginate(PageRequest.of(Math.max(0, firstResult), Math.max(0, maxResults)));
            return this;
        }

        @Override
        public List<ProcessInstance> list() {
            return run(() -> delegate.fetch().stream()
                    .map(Jbpm4ModelAdapters::wrapProcessInstance)
                    .toList());
        }

        @Override
        public ProcessInstance uniqueResult() {
            return Jbpm4ModelAdapters.wrapProcessInstance(run(() -> delegate.fetchOne().orElse(null)));
        }

        @Override
        public long count() {
            return run(delegate::count);
        }

        private io.github.parkiyong.jaybee.api.ProcessInstanceQuery.SortField mapSortField(String prop) {
            if (ProcessInstanceQuery.PROPERTY_KEY.equals(prop)) {
                return io.github.parkiyong.jaybee.api.ProcessInstanceQuery.SortField.BUSINESS_KEY;
            }
            return io.github.parkiyong.jaybee.api.ProcessInstanceQuery.SortField.ID;
        }
    }
}
