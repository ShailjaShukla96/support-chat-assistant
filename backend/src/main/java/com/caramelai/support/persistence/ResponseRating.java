package com.caramelai.support.persistence;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/** A helpful / not-helpful rating. One row per assistant message ({@code messageId} is unique); upserted. */
@Table("RESPONSE_RATING")
public record ResponseRating(
        @Id UUID id, UUID messageId, @Column("RATING_VALUE") RatingValue value, Instant updatedAt) {}
