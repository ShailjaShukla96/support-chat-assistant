package com.caramelai.support.api.dto;

import com.caramelai.support.persistence.EscalationReason;

/** Optional body. A missing or null reason means CUSTOMER_REQUESTED. */
public record EscalationRequestBody(EscalationReason reason) {}
