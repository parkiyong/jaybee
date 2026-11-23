package io.github.parkiyong.jaybee.api.exception;

/**
 * Thrown when a jPDL XML definition is malformed, violates structural rules,
 * or contains an unsupported element.
 */
public class JpdlParseException extends JaybeeException {

    public JpdlParseException(String message) {
        super(message);
    }

    public JpdlParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
