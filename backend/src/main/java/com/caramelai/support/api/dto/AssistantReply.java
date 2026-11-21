package com.caramelai.support.api.dto;

import com.caramelai.support.persistence.Outcome;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

/** {@code escalationId} is present only when the outcome is ESCALATED. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AssistantReply(UUID messageId, String text, Outcome outcome, boolean escalationOffered, UUID escalationId) {

    public static AssistantReply of(UUID messageId, String text, Outcome outcome, boolean escalationOffered) {
        return new AssistantReply(messageId, text, outcome, escalationOffered, null);
    }

    public static AssistantReply escalated(UUID messageId, String text, UUID escalationId) {
        return new AssistantReply(messageId, text, Outcome.ESCALATED, false, escalationId);
    }
}
