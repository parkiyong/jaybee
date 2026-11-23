package io.github.parkiyong.jaybee.api.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable deployed process definition containing its version metadata and activity graph.
 */
public final class ProcessDefinition {

    private final String id;
    private final String deploymentId;
    private final String key;
    private final String name;
    private final int version;
    private final String description;
    private final String imageResourceName;
    private final boolean suspended;
    private final List<ActivityDefinition> activities;
    private final Map<String, ActivityDefinition> activitiesByName;
    private final Map<String, List<TransitionDefinition>> incomingByActivity;

    public ProcessDefinition(
            String id,
            String deploymentId,
            String key,
            String name,
            int version,
            String description,
            String imageResourceName,
            boolean suspended,
            List<ActivityDefinition> activities
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.deploymentId = Objects.requireNonNull(deploymentId, "deploymentId");
        this.key = Objects.requireNonNull(key, "key");
        this.name = Objects.requireNonNull(name, "name");
        this.version = version;
        this.description = description;
        this.imageResourceName = imageResourceName;
        this.suspended = suspended;
        this.activities = List.copyOf(Objects.requireNonNull(activities, "activities"));

        Map<String, ActivityDefinition> byName = new LinkedHashMap<>();
        Map<String, List<TransitionDefinition>> incoming = new LinkedHashMap<>();
        for (ActivityDefinition activity : this.activities) {
            byName.put(activity.getName(), activity);
            incoming.put(activity.getName(), new ArrayList<>());
        }
        for (ActivityDefinition activity : this.activities) {
            for (TransitionDefinition transition : activity.getOutgoingTransitions()) {
                List<TransitionDefinition> targetIncoming = incoming.get(transition.destinationName());
                if (targetIncoming != null) {
                    targetIncoming.add(transition);
                }
            }
        }
        Map<String, List<TransitionDefinition>> immutableIncoming = new LinkedHashMap<>();
        for (Map.Entry<String, List<TransitionDefinition>> entry : incoming.entrySet()) {
            immutableIncoming.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        this.activitiesByName = Collections.unmodifiableMap(byName);
        this.incomingByActivity = Collections.unmodifiableMap(immutableIncoming);
    }

    public ProcessDefinition withSuspended(boolean newSuspended) {
        return new ProcessDefinition(
                id, deploymentId, key, name, version, description, imageResourceName, newSuspended, activities
        );
    }

    public String getId() {
        return id;
    }

    public String getDeploymentId() {
        return deploymentId;
    }

    public String getKey() {
        return key;
    }

    public String getName() {
        return name;
    }

    public int getVersion() {
        return version;
    }

    public String getDescription() {
        return description;
    }

    public String getImageResourceName() {
        return imageResourceName;
    }

    public boolean isSuspended() {
        return suspended;
    }

    public List<ActivityDefinition> getActivities() {
        return activities;
    }

    public Map<String, ActivityDefinition> getActivitiesMap() {
        return activitiesByName;
    }

    public ActivityDefinition getActivity(String activityName) {
        return activitiesByName.get(activityName);
    }

    public List<ActivityDefinition> getStartActivities() {
        List<ActivityDefinition> starts = new ArrayList<>();
        for (ActivityDefinition activity : activities) {
            if (activity.getType() == ActivityType.START) {
                starts.add(activity);
            }
        }
        return List.copyOf(starts);
    }

    public List<TransitionDefinition> getIncomingTransitions(String activityName) {
        return incomingByActivity.getOrDefault(activityName, List.of());
    }
}
