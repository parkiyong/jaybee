package io.github.parkiyong.jaybee.api.spi;

import io.github.parkiyong.jaybee.api.model.MutableExecution;

/**
 * SPI allowing adapter modules to invoke external decision handler types
 * (for example, {@code org.jbpm.api.jpdl.DecisionHandler}) without coupling core to external APIs.
 */
public interface DecisionHandlerBridge {

    boolean supports(Object handlerInstance);

    String invoke(Object handlerInstance, MutableExecution execution);
}
