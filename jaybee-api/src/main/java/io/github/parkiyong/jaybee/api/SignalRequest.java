package io.github.parkiyong.jaybee.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable command describing a signal delivered to a waiting execution.
 */
public record SignalRequest(
        String executionId,
        String transitionName,
        Map<String, Object> payload
) {

    public SignalRequest {
        Objects.requireNonNull(executionId, "executionId");
        if (payload == null || payload.isEmpty()) {
            payload = Map.of();
        } else {
            payload = Collections.unmodifiableMap(new LinkedHashMap<>(payload));
        }
    }

    public static SignalRequest forExecution(String executionId) {
        return new SignalRequest(executionId, null, Map.of());
    }

    public SignalRequest withTransition(String newTransitionName) {
        return new SignalRequest(executionId, newTransitionName, payload);
    }

    public SignalRequest withPayload(Map<String, ?> additionalPayload) {
        if (additionalPayload == null || additionalPayload.isEmpty()) {
            return this;
        }
        Map<String, Object> merged = new LinkedHashMap<>(payload);
        merged.putAll(additionalPayload);
        return new SignalRequest(executionId, transitionName, merged);
    }

    public SignalRequest withVariable(String name, Object value) {
        Objects.requireNonNull(name, "name");
        Map<String, Object> merged = new LinkedHashMap<>(payload);
        merged.put(name, value);
        return new SignalRequest(executionId, transitionName, merged);
    }
}
