package io.github.parkiyong.jaybee.core.engine;

import io.github.parkiyong.jaybee.api.DeploymentBuilder;
import io.github.parkiyong.jaybee.api.DeploymentQuery;
import io.github.parkiyong.jaybee.api.ProcessDefinitionQuery;
import io.github.parkiyong.jaybee.api.RepositoryService;
import io.github.parkiyong.jaybee.api.exception.JaybeeException;
import io.github.parkiyong.jaybee.api.model.ActivityDefinition;
import io.github.parkiyong.jaybee.api.model.Coordinates;
import io.github.parkiyong.jaybee.api.model.Deployment;
import io.github.parkiyong.jaybee.api.model.DeploymentStatus;
import io.github.parkiyong.jaybee.api.model.ExecutionStatus;
import io.github.parkiyong.jaybee.api.model.ProcessDefinition;
import io.github.parkiyong.jaybee.api.query.PageRequest;
import io.github.parkiyong.jaybee.api.query.SortOrder;
import io.github.parkiyong.jaybee.api.spi.DeploymentRecord;
import io.github.parkiyong.jaybee.api.spi.ExecutionNodeData;
import io.github.parkiyong.jaybee.api.spi.ParsedProcessDefinition;
import io.github.parkiyong.jaybee.api.spi.ProcessDefinitionParser;
import io.github.parkiyong.jaybee.api.spi.ProcessStore;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Default {@link RepositoryService} backed by {@link ProcessStore} and pluggable {@link ProcessDefinitionParser}s.
 */
final class RepositoryServiceImpl implements RepositoryService {

    private final ProcessStore store;
    private final List<ProcessDefinitionParser> parsers;

    RepositoryServiceImpl(ProcessStore store, List<ProcessDefinitionParser> parsers) {
        this.store = Objects.requireNonNull(store, "store");
        this.parsers = List.copyOf(parsers);
    }

    @Override
    public DeploymentBuilder newDeployment() {
        return new DeploymentBuilderImpl();
    }

    @Override
    public DeploymentQuery deployments() {
        return new DeploymentQueryImpl();
    }

    @Override
    public ProcessDefinitionQuery definitions() {
        return new ProcessDefinitionQueryImpl();
    }

    @Override
    public Optional<ProcessDefinition> findDefinition(String definitionId) {
        Objects.requireNonNull(definitionId, "definitionId");
        return store.findProcessDefinitionById(definitionId);
    }

    @Override
    public void removeDeployment(String deploymentId, boolean cascade) {
        Objects.requireNonNull(deploymentId, "deploymentId");
        store.executeInTransaction(() -> {
            List<ProcessDefinition> defs = store.findProcessDefinitionsByDeploymentId(deploymentId);
            for (ProcessDefinition def : defs) {
                List<String> instanceIds = store.findProcessInstanceIdsByProcessDefinitionId(def.getId());
                if (!instanceIds.isEmpty()) {
                    if (!cascade) {
                        throw new JaybeeException(
                                "Cannot delete deployment '" + deploymentId
                                        + "': active process instances exist for process definition '"
                                        + def.getId() + "'"
                        );
                    }
                    for (String instanceId : instanceIds) {
                        store.deleteExecutionTree(instanceId);
                    }
                }
            }
            store.deleteProcessDefinitionsByDeployment(deploymentId);
            store.deleteDeployment(deploymentId);
            return null;
        });
    }

    @Override
    public void suspendDeployment(String deploymentId) {
        Objects.requireNonNull(deploymentId, "deploymentId");
        store.executeInTransaction(() -> {
            store.findDeploymentById(deploymentId)
                    .orElseThrow(() -> new JaybeeException("Deployment not found: " + deploymentId));
            store.updateDeploymentStatus(deploymentId, DeploymentStatus.SUSPENDED);
            store.updateProcessDefinitionsSuspendedByDeployment(deploymentId, true);
            List<ProcessDefinition> defs = store.findProcessDefinitionsByDeploymentId(deploymentId);
            for (ProcessDefinition def : defs) {
                for (String piId : store.findProcessInstanceIdsByProcessDefinitionId(def.getId())) {
                    updateInstanceSuspended(piId, true);
                }
            }
            return null;
        });
    }

    @Override
    public void resumeDeployment(String deploymentId) {
        Objects.requireNonNull(deploymentId, "deploymentId");
        store.executeInTransaction(() -> {
            store.findDeploymentById(deploymentId)
                    .orElseThrow(() -> new JaybeeException("Deployment not found: " + deploymentId));
            store.updateDeploymentStatus(deploymentId, DeploymentStatus.ACTIVE);
            store.updateProcessDefinitionsSuspendedByDeployment(deploymentId, false);
            List<ProcessDefinition> defs = store.findProcessDefinitionsByDeploymentId(deploymentId);
            for (ProcessDefinition def : defs) {
                for (String piId : store.findProcessInstanceIdsByProcessDefinitionId(def.getId())) {
                    updateInstanceSuspended(piId, false);
                }
            }
            return null;
        });
    }

    private void updateInstanceSuspended(String processInstanceId, boolean suspend) {
        store.executeForProcessInstance(processInstanceId, () -> {
            List<ExecutionNodeData> nodes = store.findExecutionTreeByProcessInstanceId(processInstanceId).orElse(null);
            if (nodes == null || nodes.isEmpty()) {
                return null;
            }
            long version = nodes.stream().filter(ExecutionNodeData::isRoot).findFirst()
                    .map(ExecutionNodeData::version).orElse(0L);
            List<ExecutionNodeData> updated = new ArrayList<>(nodes.size());
            for (ExecutionNodeData n : nodes) {
                ExecutionStatus newStatus = n.status();
                if (suspend && n.status().isRunning()) {
                    newStatus = ExecutionStatus.SUSPENDED;
                } else if (!suspend && n.status() == ExecutionStatus.SUSPENDED) {
                    newStatus = n.isRoot() ? ExecutionStatus.RUNNING : ExecutionStatus.RUNNING_BRANCH;
                }
                updated.add(new ExecutionNodeData(
                        n.id(),
                        n.processInstanceId(),
                        n.parentId(),
                        n.processDefinitionId(),
                        n.key(),
                        n.name(),
                        newStatus,
                        n.terminalOutcome(),
                        n.priority(),
                        n.currentActivityName(),
                        n.variables(),
                        n.version()
                ));
            }
            store.saveExecutionTree(processInstanceId, updated, version);
            return null;
        });
    }

    @Override
    public Set<String> listResourceNames(String deploymentId) {
        Objects.requireNonNull(deploymentId, "deploymentId");
        return store.findDeploymentById(deploymentId)
                .map(r -> r.deployment().resourceNames())
                .orElse(Set.of());
    }

    @Override
    public Optional<InputStream> openResource(String deploymentId, String resourceName) {
        Objects.requireNonNull(deploymentId, "deploymentId");
        Objects.requireNonNull(resourceName, "resourceName");
        return store.findDeploymentById(deploymentId)
                .map(r -> r.resourceBytes(resourceName))
                .map(ByteArrayInputStream::new);
    }

    @Override
    public List<String> listStartActivities(String definitionId) {
        ProcessDefinition def = store.findProcessDefinitionById(definitionId)
                .orElseThrow(() -> new JaybeeException("Process definition not found: " + definitionId));
        return def.getStartActivities().stream()
                .map(ActivityDefinition::getName)
                .toList();
    }

    @Override
    public Optional<Coordinates> findActivityCoordinates(String definitionId, String activityName) {
        ProcessDefinition def = store.findProcessDefinitionById(definitionId)
                .orElseThrow(() -> new JaybeeException("Process definition not found: " + definitionId));
        ActivityDefinition activity = def.getActivity(activityName);
        return activity == null ? Optional.empty() : Optional.ofNullable(activity.getCoordinates());
    }

    private final class DeploymentBuilderImpl implements DeploymentBuilder {
        private String name;
        private Instant deployedAt = Instant.now();
        private final Map<String, byte[]> resources = new LinkedHashMap<>();

        @Override
        public DeploymentBuilder named(String name) {
            this.name = name;
            return this;
        }

        @Override
        public DeploymentBuilder deployedAt(Instant timestamp) {
            this.deployedAt = Objects.requireNonNull(timestamp, "timestamp");
            return this;
        }

        @Override
        public DeploymentBuilder addStringResource(String resourceName, String utf8Content) {
            Objects.requireNonNull(resourceName, "resourceName");
            Objects.requireNonNull(utf8Content, "utf8Content");
            resources.put(resourceName, utf8Content.getBytes(StandardCharsets.UTF_8));
            return this;
        }

        @Override
        public DeploymentBuilder addClasspathResource(String resourcePath) {
            Objects.requireNonNull(resourcePath, "resourcePath");
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            if (cl == null) {
                cl = RepositoryServiceImpl.class.getClassLoader();
            }
            InputStream stream = cl.getResourceAsStream(resourcePath);
            if (stream == null && resourcePath.startsWith("/")) {
                stream = cl.getResourceAsStream(resourcePath.substring(1));
            }
            if (stream == null) {
                throw new JaybeeException("Classpath resource not found: " + resourcePath);
            }
            try (InputStream in = stream) {
                resources.put(resourcePath, readAllBytes(in));
            } catch (IOException e) {
                throw new JaybeeException("Failed to read classpath resource: " + resourcePath, e);
            }
            return this;
        }

        @Override
        public DeploymentBuilder addStreamResource(String resourceName, InputStream inputStream) {
            Objects.requireNonNull(resourceName, "resourceName");
            Objects.requireNonNull(inputStream, "inputStream");
            try {
                resources.put(resourceName, readAllBytes(inputStream));
            } catch (IOException e) {
                throw new JaybeeException("Failed to read input stream for resource: " + resourceName, e);
            }
            return this;
        }

        @Override
        public DeploymentBuilder addFileResource(Path filePath) {
            Objects.requireNonNull(filePath, "filePath");
            try {
                String resourceName = filePath.getFileName() != null
                        ? filePath.getFileName().toString()
                        : filePath.toString();
                resources.put(resourceName, Files.readAllBytes(filePath));
            } catch (IOException e) {
                throw new JaybeeException("Failed to read file: " + filePath.toAbsolutePath(), e);
            }
            return this;
        }

        @Override
        public DeploymentBuilder addUrlResource(URL url) {
            Objects.requireNonNull(url, "url");
            try (InputStream in = url.openStream()) {
                resources.put(url.toString(), readAllBytes(in));
            } catch (IOException e) {
                throw new JaybeeException("Failed to read URL: " + url, e);
            }
            return this;
        }

        @Override
        public DeploymentBuilder addZipArchive(ZipInputStream zipStream) {
            Objects.requireNonNull(zipStream, "zipStream");
            try {
                ZipEntry entry;
                while ((entry = zipStream.getNextEntry()) != null) {
                    if (!entry.isDirectory()) {
                        resources.put(entry.getName(), readAllBytes(zipStream));
                    }
                    zipStream.closeEntry();
                }
            } catch (IOException e) {
                throw new JaybeeException("Failed to read ZIP deployment archive", e);
            }
            return this;
        }

        @Override
        public String deploy() {
            return store.executeInTransaction(() -> {
                String deploymentId = store.nextId("dep");
                List<ParsedProcessDefinition> parsedDefs = new ArrayList<>();

                for (Map.Entry<String, byte[]> entry : resources.entrySet()) {
                    String resourceName = entry.getKey();
                    for (ProcessDefinitionParser parser : parsers) {
                        if (parser.supports(resourceName)) {
                            parsedDefs.add(parser.parse(resourceName, entry.getValue()));
                            break;
                        }
                    }
                }

                Deployment deployment = new Deployment(
                        deploymentId,
                        name,
                        DeploymentStatus.ACTIVE,
                        deployedAt,
                        resources.keySet()
                );
                store.saveDeployment(new DeploymentRecord(deployment, resources));

                for (ParsedProcessDefinition parsed : parsedDefs) {
                    int version = parsed.explicitVersion() != null
                            ? parsed.explicitVersion()
                            : store.nextProcessDefinitionVersion(parsed.key());
                    String definitionId = parsed.key() + "-" + version;
                    if (store.findProcessDefinitionById(definitionId).isPresent()) {
                        throw new JaybeeException("Process definition already exists: " + definitionId);
                    }
                    ProcessDefinition def = new ProcessDefinition(
                            definitionId,
                            deploymentId,
                            parsed.key(),
                            parsed.name(),
                            version,
                            parsed.description(),
                            parsed.imageResourceName(),
                            false,
                            parsed.activities()
                    );
                    store.saveProcessDefinition(def);
                }
                return deploymentId;
            });
        }

        private byte[] readAllBytes(InputStream in) throws IOException {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int read;
            while ((read = in.read(buf)) != -1) {
                out.write(buf, 0, read);
            }
            return out.toByteArray();
        }
    }

    private final class DeploymentQueryImpl implements DeploymentQuery {
        private String deploymentId;
        private DeploymentStatus statusFilter;
        private SortField sortField;
        private SortOrder sortOrder = SortOrder.ASC;
        private PageRequest page = PageRequest.unbounded();

        @Override
        public DeploymentQuery withId(String deploymentId) {
            this.deploymentId = deploymentId;
            return this;
        }

        @Override
        public DeploymentQuery withStatus(DeploymentStatus status) {
            this.statusFilter = status;
            return this;
        }

        @Override
        public DeploymentQuery sortBy(SortField field, SortOrder order) {
            this.sortField = Objects.requireNonNull(field, "field");
            this.sortOrder = Objects.requireNonNull(order, "order");
            return this;
        }

        @Override
        public DeploymentQuery paginate(PageRequest page) {
            this.page = Objects.requireNonNull(page, "page");
            return this;
        }

        @Override
        public List<Deployment> fetch() {
            List<Deployment> filtered = filter();
            if (sortField != null) {
                Comparator<Deployment> cmp = switch (sortField) {
                    case DEPLOYED_AT -> Comparator.comparing(Deployment::deployedAt);
                    case STATUS -> Comparator.comparing(d -> d.status().name());
                    case ID -> Comparator.comparing(Deployment::id);
                };
                if (sortOrder == SortOrder.DESC) {
                    cmp = cmp.reversed();
                }
                filtered.sort(cmp);
            }
            return applyPage(filtered, page);
        }

        @Override
        public Optional<Deployment> fetchOne() {
            List<Deployment> results = fetch();
            if (results.isEmpty()) {
                return Optional.empty();
            }
            if (results.size() > 1) {
                throw new JaybeeException("Deployment query did not return a unique result: " + results.size());
            }
            return Optional.of(results.get(0));
        }

        @Override
        public long count() {
            return filter().size();
        }

        private List<Deployment> filter() {
            List<Deployment> result = new ArrayList<>();
            for (DeploymentRecord record : store.findAllDeployments()) {
                Deployment d = record.deployment();
                if (deploymentId != null && !deploymentId.equals(d.id())) {
                    continue;
                }
                if (statusFilter != null && statusFilter != d.status()) {
                    continue;
                }
                result.add(d);
            }
            return result;
        }
    }

    private final class ProcessDefinitionQueryImpl implements ProcessDefinitionQuery {
        private String id;
        private String key;
        private String name;
        private String nameLike;
        private String deploymentId;
        private Boolean suspendedFilter;
        private SortField sortField;
        private SortOrder sortOrder = SortOrder.ASC;
        private PageRequest page = PageRequest.unbounded();

        @Override
        public ProcessDefinitionQuery withId(String definitionId) {
            this.id = definitionId;
            return this;
        }

        @Override
        public ProcessDefinitionQuery withKey(String key) {
            this.key = key;
            return this;
        }

        @Override
        public ProcessDefinitionQuery withName(String name) {
            this.name = name;
            return this;
        }

        @Override
        public ProcessDefinitionQuery withNameMatching(String sqlLikePattern) {
            this.nameLike = sqlLikePattern;
            return this;
        }

        @Override
        public ProcessDefinitionQuery inDeployment(String deploymentId) {
            this.deploymentId = deploymentId;
            return this;
        }

        @Override
        public ProcessDefinitionQuery withSuspended(boolean suspended) {
            this.suspendedFilter = suspended;
            return this;
        }

        @Override
        public ProcessDefinitionQuery sortBy(SortField field, SortOrder order) {
            this.sortField = Objects.requireNonNull(field, "field");
            this.sortOrder = Objects.requireNonNull(order, "order");
            return this;
        }

        @Override
        public ProcessDefinitionQuery paginate(PageRequest page) {
            this.page = Objects.requireNonNull(page, "page");
            return this;
        }

        @Override
        public List<ProcessDefinition> fetch() {
            List<ProcessDefinition> filtered = filter();
            if (sortField != null) {
                Map<String, Instant> depTimestamps = new LinkedHashMap<>();
                if (sortField == SortField.DEPLOYED_AT) {
                    for (DeploymentRecord dr : store.findAllDeployments()) {
                        depTimestamps.put(dr.deployment().id(), dr.deployment().deployedAt());
                    }
                }
                Comparator<ProcessDefinition> cmp = switch (sortField) {
                    case ID -> Comparator.comparing(ProcessDefinition::getId);
                    case KEY -> Comparator.comparing(ProcessDefinition::getKey);
                    case NAME -> Comparator.comparing(ProcessDefinition::getName);
                    case VERSION -> Comparator.comparingInt(ProcessDefinition::getVersion);
                    case DEPLOYED_AT -> Comparator.comparing(
                            pd -> depTimestamps.getOrDefault(pd.getDeploymentId(), Instant.EPOCH)
                    );
                };
                if (sortOrder == SortOrder.DESC) {
                    cmp = cmp.reversed();
                }
                filtered.sort(cmp);
            }
            return applyPage(filtered, page);
        }

        @Override
        public Optional<ProcessDefinition> fetchOne() {
            List<ProcessDefinition> results = fetch();
            if (results.isEmpty()) {
                return Optional.empty();
            }
            if (results.size() > 1) {
                throw new JaybeeException(
                        "ProcessDefinition query did not return a unique result: " + results.size()
                );
            }
            return Optional.of(results.get(0));
        }

        @Override
        public long count() {
            return filter().size();
        }

        private List<ProcessDefinition> filter() {
            Pattern likeRegex = nameLike == null ? null : sqlLikeToRegex(nameLike);
            List<ProcessDefinition> result = new ArrayList<>();
            for (ProcessDefinition pd : store.findAllProcessDefinitions()) {
                if (id != null && !id.equals(pd.getId())) {
                    continue;
                }
                if (key != null && !key.equals(pd.getKey())) {
                    continue;
                }
                if (name != null && !name.equals(pd.getName())) {
                    continue;
                }
                if (likeRegex != null && !likeRegex.matcher(pd.getName()).matches()) {
                    continue;
                }
                if (deploymentId != null && !deploymentId.equals(pd.getDeploymentId())) {
                    continue;
                }
                if (suspendedFilter != null && suspendedFilter != pd.isSuspended()) {
                    continue;
                }
                result.add(pd);
            }
            return result;
        }
    }

    private static Pattern sqlLikeToRegex(String likePattern) {
        StringBuilder sb = new StringBuilder("^");
        for (int i = 0; i < likePattern.length(); i++) {
            char c = likePattern.charAt(i);
            if (c == '%') {
                sb.append(".*");
            } else if (c == '_') {
                sb.append('.');
            } else {
                sb.append(Pattern.quote(String.valueOf(c)));
            }
        }
        sb.append('$');
        return Pattern.compile(sb.toString(), Pattern.DOTALL);
    }

    private static <T> List<T> applyPage(List<T> items, PageRequest page) {
        int offset = page.offset();
        int limit = page.limit();
        if (offset >= items.size() || limit == 0) {
            return List.of();
        }
        int end = (limit == Integer.MAX_VALUE)
                ? items.size()
                : Math.min(items.size(), offset + limit);
        return List.copyOf(items.subList(offset, end));
    }
}
