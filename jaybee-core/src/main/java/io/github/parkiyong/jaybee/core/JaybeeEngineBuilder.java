package io.github.parkiyong.jaybee.core;

import io.github.parkiyong.jaybee.api.JaybeeEngine;
import io.github.parkiyong.jaybee.api.spi.DecisionHandlerBridge;
import io.github.parkiyong.jaybee.api.spi.ExpressionEvaluator;
import io.github.parkiyong.jaybee.api.spi.HandlerResolver;
import io.github.parkiyong.jaybee.api.spi.ProcessDefinitionParser;
import io.github.parkiyong.jaybee.api.spi.ProcessStore;
import io.github.parkiyong.jaybee.core.el.SimpleExpressionEvaluator;
import io.github.parkiyong.jaybee.core.engine.JaybeeEngineFactory;
import io.github.parkiyong.jaybee.core.handler.ReflectiveHandlerResolver;
import io.github.parkiyong.jaybee.core.store.InMemoryProcessStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Fluent programmatic builder for assembling a standalone {@link JaybeeEngine}.
 */
public final class JaybeeEngineBuilder {

    private ProcessStore store;
    private ExpressionEvaluator expressionEvaluator;
    private HandlerResolver handlerResolver;
    private final List<ProcessDefinitionParser> parsers = new ArrayList<>();
    private final List<DecisionHandlerBridge> decisionHandlerBridges = new ArrayList<>();

    private JaybeeEngineBuilder() {
    }

    public static JaybeeEngineBuilder create() {
        return new JaybeeEngineBuilder();
    }

    public JaybeeEngineBuilder withStore(ProcessStore store) {
        this.store = Objects.requireNonNull(store, "store");
        return this;
    }

    public JaybeeEngineBuilder withExpressionEvaluator(ExpressionEvaluator expressionEvaluator) {
        this.expressionEvaluator = Objects.requireNonNull(expressionEvaluator, "expressionEvaluator");
        return this;
    }

    public JaybeeEngineBuilder withHandlerResolver(HandlerResolver handlerResolver) {
        this.handlerResolver = Objects.requireNonNull(handlerResolver, "handlerResolver");
        return this;
    }

    public JaybeeEngineBuilder registerParser(ProcessDefinitionParser parser) {
        this.parsers.add(Objects.requireNonNull(parser, "parser"));
        return this;
    }

    public JaybeeEngineBuilder registerDecisionHandlerBridge(DecisionHandlerBridge bridge) {
        this.decisionHandlerBridges.add(Objects.requireNonNull(bridge, "bridge"));
        return this;
    }

    public JaybeeEngine build() {
        ProcessStore resolvedStore = store != null ? store : new InMemoryProcessStore();
        ExpressionEvaluator resolvedEl = expressionEvaluator != null
                ? expressionEvaluator
                : new SimpleExpressionEvaluator();
        HandlerResolver resolvedHandlerResolver = handlerResolver != null
                ? handlerResolver
                : new ReflectiveHandlerResolver();
        return JaybeeEngineFactory.create(
                resolvedStore,
                resolvedEl,
                resolvedHandlerResolver,
                parsers,
                decisionHandlerBridges
        );
    }
}
