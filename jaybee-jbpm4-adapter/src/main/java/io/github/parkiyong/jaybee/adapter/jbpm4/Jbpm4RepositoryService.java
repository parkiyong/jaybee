package io.github.parkiyong.jaybee.adapter.jbpm4;

import io.github.parkiyong.jaybee.api.DeploymentBuilder;
import io.github.parkiyong.jaybee.api.exception.JaybeeException;
import io.github.parkiyong.jaybee.api.model.Coordinates;
import io.github.parkiyong.jaybee.api.model.DeploymentStatus;
import io.github.parkiyong.jaybee.api.query.PageRequest;
import io.github.parkiyong.jaybee.api.query.SortOrder;
import org.jbpm.api.Deployment;
import org.jbpm.api.DeploymentQuery;
import org.jbpm.api.JbpmException;
import org.jbpm.api.NewDeployment;
import org.jbpm.api.ProcessDefinition;
import org.jbpm.api.ProcessDefinitionQuery;
import org.jbpm.api.RepositoryService;
import org.jbpm.api.model.ActivityCoordinates;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipInputStream;

/**
 * {@link RepositoryService} adapter delegating to Jaybee's native repository service.
 */
final class Jbpm4RepositoryService implements RepositoryService {

    private final io.github.parkiyong.jaybee.api.RepositoryService delegate;

    Jbpm4RepositoryService(io.github.parkiyong.jaybee.api.RepositoryService delegate) {
        this.delegate = delegate;
    }

    @Override
    public NewDeployment createDeployment() {
        return new Jbpm4NewDeployment(delegate.newDeployment());
    }

    @Override
    public DeploymentQuery createDeploymentQuery() {
        return new Jbpm4DeploymentQuery(delegate.deployments());
    }

    @Override
    public ProcessDefinitionQuery createProcessDefinitionQuery() {
        return new Jbpm4ProcessDefinitionQuery(delegate.definitions());
    }

    @Override
    public void deleteDeployment(String deploymentId) {
        run(() -> {
            delegate.removeDeployment(deploymentId, false);
            return null;
        });
    }

    @Override
    public void deleteDeploymentCascade(String deploymentId) {
        run(() -> {
            delegate.removeDeployment(deploymentId, true);
            return null;
        });
    }

    @Override
    public void suspendDeployment(String deploymentId) {
        run(() -> {
            delegate.suspendDeployment(deploymentId);
            return null;
        });
    }

    @Override
    public void resumeDeployment(String deploymentId) {
        run(() -> {
            delegate.resumeDeployment(deploymentId);
            return null;
        });
    }

    @Override
    public Set<String> getResourceNames(String deploymentId) {
        return run(() -> delegate.listResourceNames(deploymentId));
    }

    @Override
    public InputStream getResourceAsStream(String deploymentId, String resourceName) {
        return run(() -> delegate.openResource(deploymentId, resourceName).orElse(null));
    }

    @Override
    public List<String> getStartActivityNames(String processDefinitionId) {
        return run(() -> delegate.listStartActivities(processDefinitionId));
    }

    @Override
    public String getStartFormResourceName(String processDefinitionId, String activityName) {
        return null;
    }

    @Override
    public ActivityCoordinates getActivityCoordinates(String processDefinitionId, String activityName) {
        Coordinates coords = run(() -> delegate.findActivityCoordinates(processDefinitionId, activityName).orElse(null));
        return Jbpm4ModelAdapters.wrapCoordinates(coords);
    }

    private static <T> T run(java.util.function.Supplier<T> action) {
        try {
            return action.get();
        } catch (JaybeeException e) {
            throw new JbpmException(e.getMessage(), e);
        }
    }

    private static final class Jbpm4NewDeployment implements NewDeployment {
        private final DeploymentBuilder builder;
        private String id;
        private String name;
        private long timestamp = System.currentTimeMillis();

        Jbpm4NewDeployment(DeploymentBuilder builder) {
            this.builder = builder;
        }

        @Override
        public NewDeployment setName(String name) {
            this.name = name;
            builder.named(name);
            return this;
        }

        @Override
        public NewDeployment setTimestamp(long timestamp) {
            this.timestamp = timestamp;
            builder.deployedAt(Instant.ofEpochMilli(timestamp));
            return this;
        }

        @Override
        public NewDeployment addResourceFromString(String resourceName, String string) {
            run(() -> {
                builder.addStringResource(resourceName, string);
                return null;
            });
            return this;
        }

        @Override
        public NewDeployment addResourceFromClasspath(String resourceName) {
            run(() -> {
                builder.addClasspathResource(resourceName);
                return null;
            });
            return this;
        }

        @Override
        public NewDeployment addResourceFromInputStream(String resourceName, InputStream inputStream) {
            run(() -> {
                builder.addStreamResource(resourceName, inputStream);
                return null;
            });
            return this;
        }

        @Override
        public NewDeployment addResourceFromFile(File file) {
            run(() -> {
                builder.addFileResource(file.toPath());
                return null;
            });
            return this;
        }

        @Override
        public NewDeployment addResourceFromUrl(URL url) {
            run(() -> {
                builder.addUrlResource(url);
                return null;
            });
            return this;
        }

        @Override
        public NewDeployment addResourcesFromZipInputStream(ZipInputStream zipInputStream) {
            run(() -> {
                builder.addZipArchive(zipInputStream);
                return null;
            });
            return this;
        }

        @Override
        public String deploy() {
            this.id = run(builder::deploy);
            return this.id;
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getState() {
            return Deployment.STATE_ACTIVE;
        }

        @Override
        public long getTimestamp() {
            return timestamp;
        }
    }

    private static final class Jbpm4Deployment implements Deployment {
        private final io.github.parkiyong.jaybee.api.model.Deployment delegate;

        Jbpm4Deployment(io.github.parkiyong.jaybee.api.model.Deployment delegate) {
            this.delegate = delegate;
        }

        @Override
        public String getId() {
            return delegate.id();
        }

        @Override
        public String getName() {
            return delegate.name();
        }

        @Override
        public String getState() {
            return delegate.isSuspended() ? Deployment.STATE_SUSPENDED : Deployment.STATE_ACTIVE;
        }

        @Override
        public long getTimestamp() {
            return delegate.deployedAt().toEpochMilli();
        }
    }

    private static final class Jbpm4DeploymentQuery implements DeploymentQuery {
        private final io.github.parkiyong.jaybee.api.DeploymentQuery delegate;

        Jbpm4DeploymentQuery(io.github.parkiyong.jaybee.api.DeploymentQuery delegate) {
            this.delegate = delegate;
        }

        @Override
        public DeploymentQuery deploymentId(String id) {
            delegate.withId(id);
            return this;
        }

        @Override
        public DeploymentQuery suspended() {
            delegate.withStatus(DeploymentStatus.SUSPENDED);
            return this;
        }

        @Override
        public DeploymentQuery notSuspended() {
            delegate.withStatus(DeploymentStatus.ACTIVE);
            return this;
        }

        @Override
        public DeploymentQuery orderAsc(String property) {
            delegate.sortBy(mapSortField(property), SortOrder.ASC);
            return this;
        }

        @Override
        public DeploymentQuery orderDesc(String property) {
            delegate.sortBy(mapSortField(property), SortOrder.DESC);
            return this;
        }

        @Override
        public DeploymentQuery page(int firstResult, int maxResults) {
            delegate.paginate(PageRequest.of(Math.max(0, firstResult), Math.max(0, maxResults)));
            return this;
        }

        @Override
        public List<Deployment> list() {
            return run(() -> delegate.fetch().stream().<Deployment>map(Jbpm4Deployment::new).toList());
        }

        @Override
        public Deployment uniqueResult() {
            io.github.parkiyong.jaybee.api.model.Deployment d = run(() -> delegate.fetchOne().orElse(null));
            return d == null ? null : new Jbpm4Deployment(d);
        }

        @Override
        public long count() {
            return run(delegate::count);
        }

        private io.github.parkiyong.jaybee.api.DeploymentQuery.SortField mapSortField(String prop) {
            if (DeploymentQuery.PROPERTY_TIMESTAMP.equals(prop)) {
                return io.github.parkiyong.jaybee.api.DeploymentQuery.SortField.DEPLOYED_AT;
            }
            if (DeploymentQuery.PROPERTY_STATE.equals(prop)) {
                return io.github.parkiyong.jaybee.api.DeploymentQuery.SortField.STATUS;
            }
            return io.github.parkiyong.jaybee.api.DeploymentQuery.SortField.ID;
        }
    }

    private static final class Jbpm4ProcessDefinition implements ProcessDefinition {
        private final io.github.parkiyong.jaybee.api.model.ProcessDefinition delegate;

        Jbpm4ProcessDefinition(io.github.parkiyong.jaybee.api.model.ProcessDefinition delegate) {
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
        public int getVersion() {
            return delegate.getVersion();
        }

        @Override
        public String getDeploymentId() {
            return delegate.getDeploymentId();
        }

        @Override
        public String getImageResourceName() {
            return delegate.getImageResourceName();
        }

        @Override
        public String getDescription() {
            return delegate.getDescription();
        }

        @Override
        public boolean isSuspended() {
            return delegate.isSuspended();
        }
    }

    private static final class Jbpm4ProcessDefinitionQuery implements ProcessDefinitionQuery {
        private final io.github.parkiyong.jaybee.api.ProcessDefinitionQuery delegate;

        Jbpm4ProcessDefinitionQuery(io.github.parkiyong.jaybee.api.ProcessDefinitionQuery delegate) {
            this.delegate = delegate;
        }

        @Override
        public ProcessDefinitionQuery processDefinitionId(String processDefinitionId) {
            delegate.withId(processDefinitionId);
            return this;
        }

        @Override
        public ProcessDefinitionQuery processDefinitionKey(String key) {
            delegate.withKey(key);
            return this;
        }

        @Override
        public ProcessDefinitionQuery processDefinitionName(String name) {
            delegate.withName(name);
            return this;
        }

        @Override
        public ProcessDefinitionQuery processDefinitionNameLike(String name) {
            delegate.withNameMatching(name);
            return this;
        }

        @Override
        public ProcessDefinitionQuery deploymentId(String deploymentId) {
            delegate.inDeployment(deploymentId);
            return this;
        }

        @Override
        public ProcessDefinitionQuery suspended() {
            delegate.withSuspended(true);
            return this;
        }

        @Override
        public ProcessDefinitionQuery notSuspended() {
            delegate.withSuspended(false);
            return this;
        }

        @Override
        public ProcessDefinitionQuery orderAsc(String property) {
            delegate.sortBy(mapSortField(property), SortOrder.ASC);
            return this;
        }

        @Override
        public ProcessDefinitionQuery orderDesc(String property) {
            delegate.sortBy(mapSortField(property), SortOrder.DESC);
            return this;
        }

        @Override
        public ProcessDefinitionQuery page(int firstResult, int maxResults) {
            delegate.paginate(PageRequest.of(Math.max(0, firstResult), Math.max(0, maxResults)));
            return this;
        }

        @Override
        public List<ProcessDefinition> list() {
            return run(() -> delegate.fetch().stream().<ProcessDefinition>map(Jbpm4ProcessDefinition::new).toList());
        }

        @Override
        public ProcessDefinition uniqueResult() {
            io.github.parkiyong.jaybee.api.model.ProcessDefinition pd = run(() -> delegate.fetchOne().orElse(null));
            return pd == null ? null : new Jbpm4ProcessDefinition(pd);
        }

        @Override
        public long count() {
            return run(delegate::count);
        }

        private io.github.parkiyong.jaybee.api.ProcessDefinitionQuery.SortField mapSortField(String prop) {
            if (ProcessDefinitionQuery.PROPERTY_ID.equals(prop)) {
                return io.github.parkiyong.jaybee.api.ProcessDefinitionQuery.SortField.ID;
            }
            if (ProcessDefinitionQuery.PROPERTY_KEY.equals(prop)) {
                return io.github.parkiyong.jaybee.api.ProcessDefinitionQuery.SortField.KEY;
            }
            if (ProcessDefinitionQuery.PROPERTY_NAME.equals(prop)) {
                return io.github.parkiyong.jaybee.api.ProcessDefinitionQuery.SortField.NAME;
            }
            if (ProcessDefinitionQuery.PROPERTY_VERSION.equals(prop)) {
                return io.github.parkiyong.jaybee.api.ProcessDefinitionQuery.SortField.VERSION;
            }
            if (ProcessDefinitionQuery.PROPERTY_DEPLOYMENT_TIMESTAMP.equals(prop)) {
                return io.github.parkiyong.jaybee.api.ProcessDefinitionQuery.SortField.DEPLOYED_AT;
            }
            return io.github.parkiyong.jaybee.api.ProcessDefinitionQuery.SortField.ID;
        }
    }
}
