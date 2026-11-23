package io.github.parkiyong.jaybee.api.model;

/**
 * Core control-flow activity types supported by Jaybee.
 */
public enum ActivityType {
    START("start"),
    STATE("state"),
    DECISION("decision"),
    FORK("fork"),
    JOIN("join"),
    END("end"),
    END_CANCEL("end-cancel"),
    END_ERROR("end-error");

    private final String xmlTag;

    ActivityType(String xmlTag) {
        this.xmlTag = xmlTag;
    }

    public String xmlTag() {
        return xmlTag;
    }

    public boolean isEnd() {
        return this == END || this == END_CANCEL || this == END_ERROR;
    }
}
