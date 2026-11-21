package com.caramelai.support.api;

import com.caramelai.support.api.dto.EscalationDetail;
import com.caramelai.support.api.dto.EscalationSummary;
import com.caramelai.support.api.dto.TranscriptEntry;
import com.caramelai.support.persistence.EscalationRequest;
import com.caramelai.support.persistence.EscalationRequestRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Read-only escalation views for support agents. Guarded by AgentTokenFilter. */
@RestController
@RequestMapping("/api/agent/escalations")
public class AgentEscalationController {

    private static final int MAX_LIMIT = 200;

    private final EscalationRequestRepository repository;
    private final JsonMapper json;

    public AgentEscalationController(EscalationRequestRepository repository, JsonMapper json) {
        this.repository = repository;
        this.json = json;
    }

    @GetMapping
    public List<EscalationSummary> list(@RequestParam(defaultValue = "50") int limit) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new InvalidMessageException("limit must be between 1 and " + MAX_LIMIT + ".");
        }
        return repository.findAllByOrderByCreatedAtDesc(Limit.of(limit)).stream()
                .map(EscalationSummary::of)
                .toList();
    }

    @GetMapping("/{escalationId}")
    public EscalationDetail get(@PathVariable UUID escalationId) {
        EscalationRequest e = repository.findById(escalationId)
                .orElseThrow(() -> new SessionNotFoundException("Escalation not found."));
        List<TranscriptEntry> transcript = json.readValue(e.transcript(), new TypeReference<List<TranscriptEntry>>() {});
        return new EscalationDetail(e.id(), e.sessionId(), e.reason(), e.status(), e.createdAt(), transcript);
    }
}
