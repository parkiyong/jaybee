package io.github.parkiyong.jaybee.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable command describing how to start a new process instance.
 */
public record StartProcessRequest(
        String definitionId,
        String definitionKey,
        String businessKey,
        Map<String, Object> variables
) {

    public StartProcessRequest {
        boolean hasId = definitionId != null && !definitionId.isBlank();
        boolean hasKey = definitionKey != null && !definitionKey.isBlank();
        if (hasId == hasKey) {
            throw new IllegalArgumentException("Exactly one of definitionId or definitionKey must be provided");
        }
        if (variables == null || variables.isEmpty()) {
            variables = Map.of();
        } else {
            variables = Collections.unmodifiableMap(new LinkedHashMap<>(variables));
        }
    }

    public static StartProcessRequest byId(String definitionId) {
        Objects.requireNonNull(definitionId, "definitionId");
        return new StartProcessRequest(definitionId, null, null, Map.of());
    }

    public static StartProcessRequest byKey(String definitionKey) {
        Objects.requireNonNull(definitionKey, "definitionKey");
        return new StartProcessRequest(null, definitionKey, null, Map.of());
    }

    public StartProcessRequest withBusinessKey(String newBusinessKey) {
        return new StartProcessRequest(definitionId, definitionKey, newBusinessKey, variables);
    }

    public StartProcessRequest withVariables(Map<String, ?> additionalVariables) {
        if (additionalVariables == null || additionalVariables.isEmpty()) {
            return this;
        }
        Map<String, Object> merged = new LinkedHashMap<>(variables);
        merged.putAll(additionalVariables);
        return new StartProcessRequest(definitionId, definitionKey, businessKey, merged);
    }

    public StartProcessRequest withVariable(String name, Object value) {
        Objects.requireNonNull(name, "name");
        Map<String, Object> merged = new LinkedHashMap<>(variables);
        merged.put(name, value);
        return new StartProcessRequest(definitionId, definitionKey, businessKey, merged);
    }
}
