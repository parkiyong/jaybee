package io.github.parkiyong.jaybee.api.spi;

import io.github.parkiyong.jaybee.api.model.HandlerDescriptor;

import java.util.function.Function;

/**
 * SPI for instantiating or resolving handler objects declared in a process definition.
 */
public interface HandlerResolver {

    Object resolve(
            HandlerDescriptor descriptor,
            ExpressionEvaluator expressionEvaluator,
            Function<String, Object> variableLookup
    );
}
