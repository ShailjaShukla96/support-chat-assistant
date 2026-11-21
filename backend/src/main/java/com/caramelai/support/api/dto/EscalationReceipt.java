package com.caramelai.support.api.dto;

import java.time.Instant;
import java.util.UUID;

/** {@code message} is the confirmation text to show the customer. */
public record EscalationReceipt(UUID escalationId, Instant receivedAt, String message) {}
