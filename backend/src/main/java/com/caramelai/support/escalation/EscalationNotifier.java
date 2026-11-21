package com.caramelai.support.escalation;

import com.caramelai.support.persistence.EscalationReason;
import java.util.UUID;

/**
 * Called after an escalation is recorded. Takes IDs only, never transcript text. v1 only logs; email, ticketing,
 * or live handoff plug in as new implementations once the owners choose a channel.
 */
public interface EscalationNotifier {

    void escalationRecorded(UUID escalationId, UUID sessionId, EscalationReason reason);
}
