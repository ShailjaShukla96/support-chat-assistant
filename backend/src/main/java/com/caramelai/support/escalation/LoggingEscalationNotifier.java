package com.caramelai.support.escalation;

import com.caramelai.support.persistence.EscalationReason;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LoggingEscalationNotifier implements EscalationNotifier {

    private static final Logger log = LoggerFactory.getLogger(LoggingEscalationNotifier.class);

    @Override
    public void escalationRecorded(UUID escalationId, UUID sessionId, EscalationReason reason) {
        log.atInfo()
                .setMessage("escalation.recorded")
                .addKeyValue("escalationId", escalationId.toString())
                .addKeyValue("sessionId", sessionId.toString())
                .addKeyValue("reason", reason.name())
                .log();
    }
}
