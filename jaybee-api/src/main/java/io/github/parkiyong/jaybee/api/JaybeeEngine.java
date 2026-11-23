package io.github.parkiyong.jaybee.api;

/**
 * Entry point to a Jaybee workflow engine instance.
 */
public interface JaybeeEngine extends AutoCloseable {

    RepositoryService repository();

    RuntimeService runtime();

    @Override
    void close();
}
