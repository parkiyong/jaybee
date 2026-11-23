package io.github.parkiyong.jaybee.api.exception;

/**
 * Base unchecked exception thrown by the Jaybee workflow engine.
 */
public class JaybeeException extends RuntimeException {

    public JaybeeException(String message) {
        super(message);
    }

    public JaybeeException(String message, Throwable cause) {
        super(message, cause);
    }

    public JaybeeException(Throwable cause) {
        super(cause);
    }
}
