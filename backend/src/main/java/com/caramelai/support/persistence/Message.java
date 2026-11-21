package com.caramelai.support.persistence;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * A single customer or assistant message. Outcome/escalationOffered/latency/tokens are set on assistant
 * messages only; outcome is null for user messages.
 */
@Table("MESSAGE")
public record Message(
        @Id UUID id,
        UUID sessionId,
        Role role,
        @Column("MESSAGE_TEXT") String text,
        Outcome outcome,
        boolean escalationOffered,
        String requestId,
        Integer latencyMs,
        Integer promptTokens,
        Integer completionTokens,
        Instant createdAt) {}
