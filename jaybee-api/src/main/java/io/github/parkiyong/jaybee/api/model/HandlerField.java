package io.github.parkiyong.jaybee.api.model;

import java.util.Objects;

/**
 * Field or JavaBean property injection entry attached to a handler descriptor.
 */
public record HandlerField(String name, Object value, boolean property) {

    public HandlerField {
        Objects.requireNonNull(name, "name");
    }
}
