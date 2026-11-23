package io.github.parkiyong.jaybee.api.model;

import java.util.List;

/**
 * Metadata describing how to instantiate or resolve a user-supplied handler.
 */
public record HandlerDescriptor(String className, String expression, List<HandlerField> fields) {

    public HandlerDescriptor {
        fields = fields == null ? List.of() : List.copyOf(fields);
        if ((className == null || className.isBlank()) && (expression == null || expression.isBlank())) {
            throw new IllegalArgumentException("HandlerDescriptor requires either className or expression");
        }
    }
}
