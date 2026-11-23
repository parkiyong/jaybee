package io.github.parkiyong.jaybee.api.query;

/**
 * Offset/limit pagination window for query execution.
 */
public record PageRequest(int offset, int limit) {

    private static final PageRequest UNBOUNDED = new PageRequest(0, Integer.MAX_VALUE);

    public PageRequest {
        if (offset < 0) {
            throw new IllegalArgumentException("offset must be >= 0, got " + offset);
        }
        if (limit < 0) {
            throw new IllegalArgumentException("limit must be >= 0, got " + limit);
        }
    }

    public static PageRequest of(int offset, int limit) {
        return new PageRequest(offset, limit);
    }

    public static PageRequest unbounded() {
        return UNBOUNDED;
    }
}
