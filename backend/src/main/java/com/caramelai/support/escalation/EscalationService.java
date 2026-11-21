package com.caramelai.support.escalation;

import com.caramelai.support.api.SessionNotFoundException;
import com.caramelai.support.api.dto.EscalationReceipt;
import com.caramelai.support.api.dto.TranscriptEntry;
import com.caramelai.support.config.RequestIdFilter;
import com.caramelai.support.persistence.ChatSessionRepository;
import com.caramelai.support.persistence.EscalationReason;
import com.caramelai.support.persistence.EscalationRequest;
import com.caramelai.support.persistence.EscalationStatus;
import com.caramelai.support.persistence.Message;
import com.caramelai.support.persistence.MessageRepository;
import com.caramelai.support.persistence.Store;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/** Records escalation requests with an immutable transcript snapshot (FR-009) and notifies the channel. */
@Service
public class EscalationService {

    public static final String CONFIRMATION =
            "Your request for a human agent has been recorded together with this conversation. "
                    + "A support agent will review it.";

    private final ChatSessionRepository sessions;
    private final MessageRepository messages;
    private final Store store;
    private final EscalationNotifier notifier;
    private final JsonMapper json;

    public EscalationService(
            ChatSessionRepository sessions,
            MessageRepository messages,
            Store store,
            EscalationNotifier notifier,
            JsonMapper json) {
        this.sessions = sessions;
        this.messages = messages;
        this.store = store;
        this.notifier = notifier;
        this.json = json;
    }

    public EscalationReceipt escalate(UUID sessionId, EscalationReason reason) {
        sessions.findById(sessionId).orElseThrow(() -> new SessionNotFoundException("Session not found."));

        List<TranscriptEntry> transcript = messages.findBySessionIdOrderByCreatedAtAsc(sessionId).stream()
                .map(EscalationService::entry)
                .toList();
        Instant now = Instant.now();
        EscalationRequest saved = store.insert(new EscalationRequest(
                UUID.randomUUID(),
                sessionId,
                reason == null ? EscalationReason.CUSTOMER_REQUESTED : reason,
                json.writeValueAsString(transcript),
                EscalationStatus.NEW,
                MDC.get(RequestIdFilter.MDC_KEY),
                now));

        notifier.escalationRecorded(saved.id(), sessionId, saved.reason());
        return new EscalationReceipt(saved.id(), saved.createdAt(), CONFIRMATION);
    }

    private static TranscriptEntry entry(Message m) {
        return new TranscriptEntry(m.role(), m.text(), m.createdAt());
    }
}
