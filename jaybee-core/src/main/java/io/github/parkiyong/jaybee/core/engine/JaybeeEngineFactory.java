package io.github.parkiyong.jaybee.core.engine;

import io.github.parkiyong.jaybee.api.JaybeeEngine;
import io.github.parkiyong.jaybee.api.spi.DecisionHandlerBridge;
import io.github.parkiyong.jaybee.api.spi.ExpressionEvaluator;
import io.github.parkiyong.jaybee.api.spi.HandlerResolver;
import io.github.parkiyong.jaybee.api.spi.ProcessDefinitionParser;
import io.github.parkiyong.jaybee.api.spi.ProcessStore;

import java.util.List;

/**
 * Internal factory bridge between {@link io.github.parkiyong.jaybee.core.JaybeeEngineBuilder}
 * and package-private engine implementations.
 */
public final class JaybeeEngineFactory {

    private JaybeeEngineFactory() {
    }

    public static JaybeeEngine create(
            ProcessStore store,
            ExpressionEvaluator expressionEvaluator,
            HandlerResolver handlerResolver,
            List<ProcessDefinitionParser> parsers,
            List<DecisionHandlerBridge> decisionHandlerBridges
    ) {
        return new JaybeeEngineImpl(
                store,
                expressionEvaluator,
                handlerResolver,
                parsers,
                decisionHandlerBridges
        );
    }
}
