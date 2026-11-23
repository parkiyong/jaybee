package io.github.parkiyong.jaybee.api.model;

/**
 * Controls whether an end activity terminates only the arriving execution branch
 * or the entire enclosing process instance.
 */
public enum EndScope {
    PROCESS_INSTANCE,
    EXECUTION
}
