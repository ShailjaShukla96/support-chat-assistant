package com.caramelai.support.persistence;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/** One customer's conversation. {@code userRef} is an optional host-site ID, max 128 chars, never used to personalize. */
@Table("CHAT_SESSION")
public record ChatSession(@Id UUID id, String userRef, Instant createdAt) {}
