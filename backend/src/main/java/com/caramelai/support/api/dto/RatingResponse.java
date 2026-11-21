package com.caramelai.support.api.dto;

import com.caramelai.support.persistence.RatingValue;
import java.time.Instant;
import java.util.UUID;

public record RatingResponse(UUID messageId, RatingValue value, Instant updatedAt) {}
