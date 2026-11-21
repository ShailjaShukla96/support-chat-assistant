package com.caramelai.support.persistence;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/** A recorded request for a human agent. {@code transcript} is an immutable JSON snapshot taken at request time. */
@Table("ESCALATION_REQUEST")
public record EscalationRequest(
        @Id UUID id,
        UUID sessionId,
        EscalationReason reason,
        String transcript,
        EscalationStatus status,
        String requestId,
        Instant createdAt) {}
