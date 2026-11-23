package io.github.parkiyong.jaybee.adapter.jbpm4;

import io.github.parkiyong.jaybee.api.JaybeeEngine;
import io.github.parkiyong.jaybee.core.JaybeeEngineBuilder;
import io.github.parkiyong.jaybee.jpdl.JpdlProcessParser;
import org.jbpm.api.ProcessEngine;

import java.util.Objects;

/**
 * Entry point for adapting a {@link JaybeeEngine} to the standard {@code org.jbpm.api.ProcessEngine} interface.
 */
public final class JaybeeJbpm4Adapter {

    private JaybeeJbpm4Adapter() {
    }

    /**
     * Registers the jPDL 4.4 XML parser and {@code org.jbpm.api.jpdl.DecisionHandler} bridge on a builder.
     */
    public static JaybeeEngineBuilder configureForJbpm4(JaybeeEngineBuilder builder) {
        Objects.requireNonNull(builder, "builder");
        return builder
                .registerParser(new JpdlProcessParser())
                .registerDecisionHandlerBridge(new Jbpm4DecisionHandlerBridge());
    }

    /**
     * Creates a standalone in-memory Jaybee engine configured for jPDL 4.4 and wrapped as {@link ProcessEngine}.
     */
    public static ProcessEngine createDefaultProcessEngine() {
        JaybeeEngine engine = configureForJbpm4(JaybeeEngineBuilder.create()).build();
        return wrap(engine);
    }

    /**
     * Wraps an existing {@link JaybeeEngine} behind the {@link ProcessEngine} facade.
     */
    public static ProcessEngine wrap(JaybeeEngine engine) {
        Objects.requireNonNull(engine, "engine");
        return new Jbpm4ProcessEngine(engine);
    }
}
