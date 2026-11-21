package com.caramelai.support.api.dto;

import com.caramelai.support.persistence.Role;
import java.time.Instant;

public record TranscriptEntry(Role role, String text, Instant createdAt) {}
