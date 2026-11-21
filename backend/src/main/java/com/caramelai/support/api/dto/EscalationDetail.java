package com.caramelai.support.api.dto;

import com.caramelai.support.persistence.EscalationReason;
import com.caramelai.support.persistence.EscalationStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record EscalationDetail(
        UUID escalationId,
        UUID sessionId,
        EscalationReason reason,
        EscalationStatus status,
        Instant createdAt,
        List<TranscriptEntry> transcript) {}
