package com.caramelai.support.api.dto;

/** Optional ID from the host site. Stored only; never used to personalize answers. */
public record CreateSessionRequest(String userRef) {}
