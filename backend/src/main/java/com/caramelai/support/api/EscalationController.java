package com.caramelai.support.api;

import com.caramelai.support.api.dto.EscalationReceipt;
import com.caramelai.support.api.dto.EscalationRequestBody;
import com.caramelai.support.escalation.EscalationService;
import com.caramelai.support.persistence.EscalationReason;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sessions/{sessionId}/escalations")
public class EscalationController {

    private final EscalationService escalations;

    public EscalationController(EscalationService escalations) {
        this.escalations = escalations;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EscalationReceipt request(
            @PathVariable UUID sessionId, @RequestBody(required = false) EscalationRequestBody body) {
        EscalationReason reason = body == null || body.reason() == null ? EscalationReason.CUSTOMER_REQUESTED : body.reason();
        return escalations.escalate(sessionId, reason);
    }
}
