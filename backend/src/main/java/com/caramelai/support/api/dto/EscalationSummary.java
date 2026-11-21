package com.caramelai.support.api.dto;

import com.caramelai.support.persistence.EscalationReason;
import com.caramelai.support.persistence.EscalationRequest;
import com.caramelai.support.persistence.EscalationStatus;
import java.time.Instant;
import java.util.UUID;

public record EscalationSummary(
        UUID escalationId, UUID sessionId, EscalationReason reason, EscalationStatus status, Instant createdAt) {

    public static EscalationSummary of(EscalationRequest e) {
        return new EscalationSummary(e.id(), e.sessionId(), e.reason(), e.status(), e.createdAt());
    }
}
