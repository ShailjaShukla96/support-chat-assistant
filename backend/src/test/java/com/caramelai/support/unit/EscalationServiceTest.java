package com.caramelai.support.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.caramelai.support.api.SessionNotFoundException;
import com.caramelai.support.api.dto.EscalationReceipt;
import com.caramelai.support.api.dto.TranscriptEntry;
import com.caramelai.support.chat.ChatService;
import com.caramelai.support.escalation.EscalationNotifier;
import com.caramelai.support.escalation.EscalationService;
import com.caramelai.support.escalation.LoggingEscalationNotifier;
import com.caramelai.support.persistence.EscalationReason;
import com.caramelai.support.persistence.EscalationRequest;
import com.caramelai.support.persistence.EscalationRequestRepository;
import com.caramelai.support.persistence.EscalationStatus;
import com.caramelai.support.persistence.Role;
import com.caramelai.support.support.FakeChatModel;
import com.caramelai.support.support.TestChatConfig;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestChatConfig.class)
class EscalationServiceTest {

    @Autowired EscalationService escalations;
    @Autowired ChatService chat;
    @Autowired FakeChatModel model;
    @Autowired EscalationRequestRepository repository;
    @Autowired JsonMapper json;
    @MockitoBean EscalationNotifier notifier;

    @BeforeEach
    void reset() {
        model.reset();
    }

    private List<TranscriptEntry> transcriptOf(UUID escalationId) {
        EscalationRequest stored = repository.findById(escalationId).orElseThrow();
        return json.readValue(stored.transcript(), new TypeReference<List<TranscriptEntry>>() {});
    }

    @Test
    void snapshotHoldsAllSessionMessagesInOrderAndStatusStartsNew() {
        model.thenReply("Reply one.").thenReply("Reply two.");
        UUID session = chat.createSession(null).sessionId();
        chat.sendMessage(session, "Question one");
        chat.sendMessage(session, "Question two");

        EscalationReceipt receipt = escalations.escalate(session, EscalationReason.CUSTOMER_REQUESTED);

        EscalationRequest stored = repository.findById(receipt.escalationId()).orElseThrow();
        assertThat(stored.status()).isEqualTo(EscalationStatus.NEW);
        assertThat(stored.reason()).isEqualTo(EscalationReason.CUSTOMER_REQUESTED);
        assertThat(stored.sessionId()).isEqualTo(session);
        assertThat(stored.createdAt()).isNotNull();
        assertThat(receipt.receivedAt()).isNotNull();
        assertThat(receipt.message()).isNotBlank();

        List<TranscriptEntry> transcript = transcriptOf(receipt.escalationId());
        assertThat(transcript).extracting(TranscriptEntry::text)
                .containsExactly("Question one", "Reply one.", "Question two", "Reply two.");
        assertThat(transcript).extracting(TranscriptEntry::role)
                .containsExactly(Role.USER, Role.ASSISTANT, Role.USER, Role.ASSISTANT);
        assertThat(transcript).allSatisfy(e -> assertThat(e.createdAt()).isNotNull());
    }

    @Test
    void laterMessagesDoNotChangeAnExistingSnapshotAndEachEscalationKeepsItsOwn() {
        model.thenReply("Reply one.").thenReply("Reply two.");
        UUID session = chat.createSession(null).sessionId();
        chat.sendMessage(session, "Question one");

        EscalationReceipt first = escalations.escalate(session, EscalationReason.ASSISTANT_UNSURE);
        chat.sendMessage(session, "Question two");
        EscalationReceipt second = escalations.escalate(session, EscalationReason.PROVIDER_UNAVAILABLE);

        assertThat(transcriptOf(first.escalationId())).hasSize(2);
        assertThat(transcriptOf(second.escalationId())).hasSize(4);
        assertThat(first.escalationId()).isNotEqualTo(second.escalationId());
        assertThat(repository.findById(second.escalationId()).orElseThrow().reason())
                .isEqualTo(EscalationReason.PROVIDER_UNAVAILABLE);
    }

    @Test
    void emptySessionStillProducesAValidEmptyTranscript() {
        UUID session = chat.createSession(null).sessionId();
        EscalationReceipt receipt = escalations.escalate(session, EscalationReason.CUSTOMER_REQUESTED);
        assertThat(transcriptOf(receipt.escalationId())).isEmpty();
    }

    @Test
    void unknownSessionIsNotFound() {
        assertThatThrownBy(() -> escalations.escalate(UUID.randomUUID(), EscalationReason.CUSTOMER_REQUESTED))
                .isInstanceOf(SessionNotFoundException.class);
    }

    @Test
    void notifierReceivesIdsAndReasonOnly() {
        UUID session = chat.createSession(null).sessionId();
        EscalationReceipt receipt = escalations.escalate(session, EscalationReason.CUSTOMER_REQUESTED);
        verify(notifier, times(1))
                .escalationRecorded(eq(receipt.escalationId()), eq(session), eq(EscalationReason.CUSTOMER_REQUESTED));
        verify(notifier, times(1)).escalationRecorded(any(), any(), any());
    }

    @Test
    void loggingNotifierWritesIdsAndReasonWithoutTranscriptText() {
        Logger logger = (Logger) LoggerFactory.getLogger(LoggingEscalationNotifier.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            UUID escalationId = UUID.randomUUID();
            UUID sessionId = UUID.randomUUID();
            new LoggingEscalationNotifier().escalationRecorded(escalationId, sessionId, EscalationReason.ASSISTANT_UNSURE);

            assertThat(appender.list).hasSize(1);
            ILoggingEvent event = appender.list.getFirst();
            assertThat(event.getFormattedMessage()).isEqualTo("escalation.recorded");
            String kv = event.getKeyValuePairs().stream()
                    .map(p -> p.key + "=" + p.value)
                    .collect(Collectors.joining(","));
            assertThat(kv)
                    .contains("escalationId=" + escalationId)
                    .contains("sessionId=" + sessionId)
                    .contains("reason=ASSISTANT_UNSURE");
        } finally {
            logger.detachAppender(appender);
        }
    }
}
