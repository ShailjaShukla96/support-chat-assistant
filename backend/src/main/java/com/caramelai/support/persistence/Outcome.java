package com.caramelai.support.persistence;

/** Result of an assistant message (data-model.md). Null on user messages. */
public enum Outcome {
    ANSWERED,
    NO_ANSWER,
    UNAVAILABLE,
    BLOCKED,
    ESCALATED
}
