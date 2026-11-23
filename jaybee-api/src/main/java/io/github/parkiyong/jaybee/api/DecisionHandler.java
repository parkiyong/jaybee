package io.github.parkiyong.jaybee.api;

import io.github.parkiyong.jaybee.api.model.MutableExecution;

/**
 * Native Jaybee decision handler returning the name of the outgoing transition to take.
 */
@FunctionalInterface
public interface DecisionHandler {

    String decide(MutableExecution execution);
}
