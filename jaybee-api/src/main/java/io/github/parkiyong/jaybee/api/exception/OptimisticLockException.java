package io.github.parkiyong.jaybee.api.exception;

/**
 * Thrown when a concurrent modification conflicts with the expected version of a persisted entity.
 */
public class OptimisticLockException extends JaybeeException {

    public OptimisticLockException(String message) {
        super(message);
    }
}
