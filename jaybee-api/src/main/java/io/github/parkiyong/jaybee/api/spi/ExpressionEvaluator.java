package io.github.parkiyong.jaybee.api.spi;

import java.util.function.Function;

/**
 * SPI for evaluating workflow expressions (such as {@code #{order.amount > 100}}).
 */
public interface ExpressionEvaluator {

    Object evaluate(String expression, Function<String, Object> variableLookup);

    default boolean evaluateBoolean(String expression, Function<String, Object> variableLookup) {
        Object result = evaluate(expression, variableLookup);
        if (result instanceof Boolean b) {
            return b;
        }
        if (result == null) {
            return false;
        }
        if (result instanceof String s) {
            return Boolean.parseBoolean(s.trim());
        }
        if (result instanceof Number n) {
            return n.doubleValue() != 0.0;
        }
        return true;
    }

    default String evaluateString(String expression, Function<String, Object> variableLookup) {
        Object result = evaluate(expression, variableLookup);
        return result == null ? null : String.valueOf(result);
    }
}
