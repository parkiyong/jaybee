package io.github.parkiyong.jaybee.adapter.jbpm4;

import io.github.parkiyong.jaybee.api.JaybeeEngine;
import org.jbpm.api.ExecutionService;
import org.jbpm.api.HistoryService;
import org.jbpm.api.IdentityService;
import org.jbpm.api.ManagementService;
import org.jbpm.api.ProcessEngine;
import org.jbpm.api.RepositoryService;
import org.jbpm.api.TaskService;
import org.jbpm.api.cmd.Command;

import java.sql.Connection;

/**
 * {@link ProcessEngine} adapter delegating Phase 1 services to {@link JaybeeEngine}.
 */
final class Jbpm4ProcessEngine implements ProcessEngine {

    private final JaybeeEngine delegate;
    private final RepositoryService repositoryService;
    private final ExecutionService executionService;
    private volatile String authenticatedUserId;

    Jbpm4ProcessEngine(JaybeeEngine delegate) {
        this.delegate = delegate;
        this.repositoryService = new Jbpm4RepositoryService(delegate.repository());
        this.executionService = new Jbpm4ExecutionService(delegate.runtime());
    }

    @Override
    public RepositoryService getRepositoryService() {
        return repositoryService;
    }

    @Override
    public ExecutionService getExecutionService() {
        return executionService;
    }

    @Override
    public HistoryService getHistoryService() {
        throw new UnsupportedOperationException("HistoryService is deferred to Phase 2 in Jaybee");
    }

    @Override
    public TaskService getTaskService() {
        throw new UnsupportedOperationException("TaskService is deferred to Phase 3 in Jaybee");
    }

    @Override
    public ManagementService getManagementService() {
        throw new UnsupportedOperationException("ManagementService is deferred to Phase 3 in Jaybee");
    }

    @Override
    public IdentityService getIdentityService() {
        throw new UnsupportedOperationException("IdentityService is deferred to Phase 3 in Jaybee");
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(Class<T> type) {
        if (type == RepositoryService.class) {
            return (T) repositoryService;
        }
        if (type == ExecutionService.class) {
            return (T) executionService;
        }
        if (type == ProcessEngine.class) {
            return (T) this;
        }
        if (type == JaybeeEngine.class) {
            return (T) delegate;
        }
        return null;
    }

    @Override
    public Object get(String name) {
        if ("repositoryService".equals(name)) {
            return repositoryService;
        }
        if ("executionService".equals(name)) {
            return executionService;
        }
        return null;
    }

    @Override
    public ProcessEngine setAuthenticatedUserId(String authenticatedUserId) {
        this.authenticatedUserId = authenticatedUserId;
        return this;
    }

    @Override
    public ProcessEngine setHibernateSession(Object hibernateSession) {
        throw new UnsupportedOperationException("Hibernate session integration is not used in Jaybee");
    }

    @Override
    public ProcessEngine setJdbcConnection(Connection jdbcConnection) {
        throw new UnsupportedOperationException("JDBC persistence is planned for Phase 2 in Jaybee");
    }

    @Override
    public <T> T execute(Command<T> command) {
        throw new UnsupportedOperationException("Low-level Command execution is not enabled in Jaybee Phase 1");
    }

    @Override
    public void close() {
        delegate.close();
    }

    String getAuthenticatedUserId() {
        return authenticatedUserId;
    }
}
