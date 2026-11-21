package com.caramelai.support.api.dto;

import java.time.Instant;
import java.util.UUID;

public record SessionResponse(UUID sessionId, Instant createdAt) {}
