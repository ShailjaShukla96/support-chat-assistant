package com.caramelai.support.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.caramelai.support.api.dto.AssistantReply;
import com.caramelai.support.chat.ChatService;
import com.caramelai.support.persistence.EscalationReason;
import com.caramelai.support.persistence.EscalationRequest;
import com.caramelai.support.persistence.EscalationRequestRepository;
import com.caramelai.support.persistence.Message;
import com.caramelai.support.persistence.MessageRepository;
import com.caramelai.support.persistence.Outcome;
import com.caramelai.support.support.FakeChatModel;
import com.caramelai.support.support.TestChatConfig;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestChatConfig.class)
class ChatServiceEscalationTest {

    @Autowired ChatService chat;
    @Autowired FakeChatModel model;
    @Autowired MessageRepository messages;
    @Autowired EscalationRequestRepository escalations;

    @BeforeEach
    void reset() {
        model.reset();
    }

    private List<EscalationRequest> escalationsOf(UUID sessionId) {
        return escalations.findAll().stream().filter(e -> e.sessionId().equals(sessionId)).toList();
    }

    @Test
    void freeTextHumanRequestSkipsTheModelAndRecordsAnEscalation() {
        UUID session = chat.createSession(null).sessionId();

        AssistantReply reply = chat.sendMessage(session, "I want to talk to a person");

        assertThat(model.callCount()).isZero();
        assertThat(reply.outcome()).isEqualTo(Outcome.ESCALATED);
        assertThat(reply.escalationId()).isNotNull();
        assertThat(reply.text()).isNotBlank();

        List<EscalationRequest> recorded = escalationsOf(session);
        assertThat(recorded).hasSize(1);
        assertThat(recorded.getFirst().id()).isEqualTo(reply.escalationId());
        assertThat(recorded.getFirst().reason()).isEqualTo(EscalationReason.CUSTOMER_REQUESTED);
        // the customer's own message is part of the transcript
        assertThat(recorded.getFirst().transcript()).contains("I want to talk to a person");

        Message assistant = messages.findBySessionIdOrderByCreatedAtAsc(session).getLast();
        assertThat(assistant.outcome()).isEqualTo(Outcome.ESCALATED);
        assertThat(assistant.id()).isEqualTo(reply.messageId());
    }

    @Test
    void modelReplyStartingWithTheMarkerBecomesNoAnswerAndOffersEscalation() {
        model.thenReply("[[NO_ANSWER]] I do not have information about that. I can connect you with a human agent.");
        UUID session = chat.createSession(null).sessionId();

        AssistantReply reply = chat.sendMessage(session, "Does Caramel AI offer a quantum API?");

        assertThat(reply.outcome()).isEqualTo(Outcome.NO_ANSWER);
        assertThat(reply.escalationOffered()).isTrue();
        assertThat(reply.text())
                .isEqualTo("I do not have information about that. I can connect you with a human agent.")
                .doesNotContain("[[NO_ANSWER]]");
        Message stored = messages.findBySessionIdOrderByCreatedAtAsc(session).getLast();
        assertThat(stored.outcome()).isEqualTo(Outcome.NO_ANSWER);
        assertThat(stored.text()).doesNotContain("NO_ANSWER");
        assertThat(escalationsOf(session)).isEmpty(); // only an offer, nothing recorded yet
    }

    @Test
    void markerWithLeadingWhitespaceIsStillRecognized() {
        model.thenReply("\n  [[NO_ANSWER]]\nI do not know.");
        AssistantReply reply = chat.sendMessage(chat.createSession(null).sessionId(), "something obscure");
        assertThat(reply.outcome()).isEqualTo(Outcome.NO_ANSWER);
        assertThat(reply.text()).isEqualTo("I do not know.");
    }

    @Test
    void markerOnlyReplyGetsADefaultDoNotKnowText() {
        model.thenReply("[[NO_ANSWER]]");
        AssistantReply reply = chat.sendMessage(chat.createSession(null).sessionId(), "something obscure");
        assertThat(reply.outcome()).isEqualTo(Outcome.NO_ANSWER);
        assertThat(reply.text()).containsIgnoringCase("don't know").containsIgnoringCase("human agent");
    }

    @Test
    void replyWithoutTheMarkerStaysAnswered() {
        model.thenReply("A 429 means you hit the rate limit.");
        AssistantReply reply = chat.sendMessage(chat.createSession(null).sessionId(), "Why 429?");
        assertThat(reply.outcome()).isEqualTo(Outcome.ANSWERED);
        assertThat(reply.escalationOffered()).isFalse();
    }

    @Test
    void markerInTheMiddleOfAReplyIsNotTreatedAsNoAnswer() {
        model.thenReply("Here is the answer. Note that [[NO_ANSWER]] is just text.");
        AssistantReply reply = chat.sendMessage(chat.createSession(null).sessionId(), "Why 429?");
        assertThat(reply.outcome()).isEqualTo(Outcome.ANSWERED);
    }

    @Test
    void partialAnswerKeepsTheAnsweredPartAndOffersEscalation() {
        model.thenReply("[[NO_ANSWER]] A 429 means you hit the rate limit. I do not know your exact plan limit; "
                + "a human agent can confirm it.");
        AssistantReply reply = chat.sendMessage(chat.createSession(null).sessionId(), "Why 429 and what is my limit?");
        assertThat(reply.outcome()).isEqualTo(Outcome.NO_ANSWER);
        assertThat(reply.escalationOffered()).isTrue();
        assertThat(reply.text()).startsWith("A 429 means you hit the rate limit.");
    }

    @Test
    void unavailableAndBlockedStillOfferEscalation() {
        model.thenThrow(new RuntimeException("boom")).thenThrow(new RuntimeException("boom"));
        AssistantReply unavailable = chat.sendMessage(chat.createSession(null).sessionId(), "hello");
        assertThat(unavailable.outcome()).isEqualTo(Outcome.UNAVAILABLE);
        assertThat(unavailable.escalationOffered()).isTrue();
    }

    @Test
    void humanRequestEscalatesEvenAfterAnEarlierEscalationInTheSameSession() {
        UUID session = chat.createSession(null).sessionId();
        chat.sendMessage(session, "talk to a human");
        chat.sendMessage(session, "talk to a human please");
        assertThat(escalationsOf(session)).hasSize(2);
    }
}
