package io.github.parkiyong.jaybee.adapter.jbpm4;

import io.github.parkiyong.jaybee.api.model.MutableExecution;
import io.github.parkiyong.jaybee.api.spi.DecisionHandlerBridge;
import org.jbpm.api.jpdl.DecisionHandler;

/**
 * Bridges {@code org.jbpm.api.jpdl.DecisionHandler} implementations into Jaybee's core runtime.
 */
public final class Jbpm4DecisionHandlerBridge implements DecisionHandlerBridge {

    @Override
    public boolean supports(Object handlerInstance) {
        return handlerInstance instanceof DecisionHandler;
    }

    @Override
    public String invoke(Object handlerInstance, MutableExecution execution) {
        DecisionHandler handler = (DecisionHandler) handlerInstance;
        return handler.decide(Jbpm4ModelAdapters.wrapOpenExecution(execution));
    }
}
