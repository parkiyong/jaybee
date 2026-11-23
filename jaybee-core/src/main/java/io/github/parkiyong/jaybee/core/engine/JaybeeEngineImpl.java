package io.github.parkiyong.jaybee.core.engine;

import io.github.parkiyong.jaybee.api.JaybeeEngine;
import io.github.parkiyong.jaybee.api.RepositoryService;
import io.github.parkiyong.jaybee.api.RuntimeService;
import io.github.parkiyong.jaybee.api.spi.DecisionHandlerBridge;
import io.github.parkiyong.jaybee.api.spi.ExpressionEvaluator;
import io.github.parkiyong.jaybee.api.spi.HandlerResolver;
import io.github.parkiyong.jaybee.api.spi.ProcessDefinitionParser;
import io.github.parkiyong.jaybee.api.spi.ProcessStore;

import java.util.List;

/**
 * Default {@link JaybeeEngine} implementation wiring services, runtime, and persistence SPI.
 */
final class JaybeeEngineImpl implements JaybeeEngine {

    private final ProcessStore store;
    private final RepositoryService repositoryService;
    private final RuntimeService runtimeService;

    JaybeeEngineImpl(
            ProcessStore store,
            ExpressionEvaluator expressionEvaluator,
            HandlerResolver handlerResolver,
            List<ProcessDefinitionParser> parsers,
            List<DecisionHandlerBridge> decisionHandlerBridges
    ) {
        this.store = store;
        this.repositoryService = new RepositoryServiceImpl(store, parsers);
        ExecutionRuntime runtime = new ExecutionRuntime(
                store,
                expressionEvaluator,
                handlerResolver,
                decisionHandlerBridges
        );
        this.runtimeService = new RuntimeServiceImpl(store, runtime);
    }

    @Override
    public RepositoryService repository() {
        return repositoryService;
    }

    @Override
    public RuntimeService runtime() {
        return runtimeService;
    }

    @Override
    public void close() {
        store.close();
    }
}
