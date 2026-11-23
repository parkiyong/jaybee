package io.github.parkiyong.jaybee.api.model;

/**
 * Strongly-typed lifecycle status for an execution node in a Jaybee process instance.
 */
public enum ExecutionStatus {
    NEW,
    RUNNING,
    RUNNING_BRANCH,
    WAITING_FOR_BRANCHES,
    AWAITING_JOIN,
    WAITING_FOR_SCOPE,
    SUSPENDED,
    COMPLETED,
    CANCELLED,
    FAILED;

    public boolean isRunning() {
        return this == RUNNING || this == RUNNING_BRANCH;
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED || this == FAILED;
    }
}
