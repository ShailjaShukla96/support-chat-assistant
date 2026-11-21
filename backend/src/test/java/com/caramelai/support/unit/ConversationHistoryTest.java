package com.caramelai.support.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.caramelai.support.chat.ConversationHistory;
import com.caramelai.support.persistence.ChatSession;
import com.caramelai.support.persistence.Message;
import com.caramelai.support.persistence.Outcome;
import com.caramelai.support.persistence.Role;
import com.caramelai.support.persistence.Store;
import com.caramelai.support.support.TestChatConfig;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestChatConfig.class)
class ConversationHistoryTest {

    private static final Instant T0 = Instant.parse("2026-11-14T10:00:00Z");

    @Autowired Store store;
    @Autowired ConversationHistory history;

    private UUID newSession() {
        return store.insert(new ChatSession(UUID.randomUUID(), null, Instant.now())).id();
    }

    private void user(UUID s, String text, int sec) {
        store.insert(new Message(UUID.randomUUID(), s, Role.USER, text, null, false, null, null, null, null, T0.plusSeconds(sec)));
    }

    private void assistant(UUID s, String text, Outcome outcome, int sec) {
        store.insert(new Message(UUID.randomUUID(), s, Role.ASSISTANT, text, outcome, false, null, null, null, null, T0.plusSeconds(sec)));
    }

    @Test
    void returnsMessagesOldestFirstAsUserAndAssistantMessages() {
        UUID s = newSession();
        user(s, "q1", 1);
        assistant(s, "a1", Outcome.ANSWERED, 2);
        user(s, "q2", 3);

        var result = history.recent(s);

        assertThat(result).extracting(m -> m.getText()).containsExactly("q1", "a1", "q2");
        assertThat(result).extracting(m -> m.getMessageType())
                .containsExactly(MessageType.USER, MessageType.ASSISTANT, MessageType.USER);
    }

    @Test
    void includesNoAnswerButExcludesUnavailableBlockedAndEscalated() {
        UUID s = newSession();
        user(s, "q1", 1);
        assistant(s, "no answer text", Outcome.NO_ANSWER, 2);
        user(s, "q2", 3);
        assistant(s, "friendly error", Outcome.UNAVAILABLE, 4);
        user(s, "q3", 5);
        assistant(s, "safe refusal", Outcome.BLOCKED, 6);
        user(s, "q4", 7);
        assistant(s, "escalation confirmation", Outcome.ESCALATED, 8);
        user(s, "q5", 9);
        assistant(s, "real answer", Outcome.ANSWERED, 10);

        var texts = history.recent(s).stream().map(m -> m.getText()).toList();

        assertThat(texts).contains("no answer text", "real answer")
                .doesNotContain("friendly error", "safe refusal", "escalation confirmation");
        assertThat(texts).containsSubsequence("q1", "no answer text", "q2", "q3", "q4", "q5", "real answer");
    }

    @Test
    void keepsOnlyTheLast20MessagesAndStartsWithAUserMessage() {
        UUID s = newSession();
        // 25 messages: U0 A1 U2 A3 ... U24
        for (int i = 0; i < 25; i++) {
            if (i % 2 == 0) {
                user(s, "m" + i, i);
            } else {
                assistant(s, "m" + i, Outcome.ANSWERED, i);
            }
        }

        List<org.springframework.ai.chat.messages.Message> result = history.recent(s);

        // last 20 are m5..m24; the leading assistant message m5 is dropped so the prompt starts with a user turn
        assertThat(result).hasSize(19);
        assertThat(result.getFirst().getText()).isEqualTo("m6");
        assertThat(result.getFirst().getMessageType()).isEqualTo(MessageType.USER);
        assertThat(result.getLast().getText()).isEqualTo("m24");
        assertThat(result).extracting(m -> m.getText()).doesNotContain("m0", "m1", "m5");
    }

    @Test
    void neverReturnsAnotherSessionsMessages() {
        UUID a = newSession();
        UUID b = newSession();
        user(a, "secret of A", 1);
        assistant(a, "reply to A", Outcome.ANSWERED, 2);
        user(b, "question of B", 3);

        assertThat(history.recent(b)).extracting(m -> m.getText()).containsExactly("question of B");
        assertThat(history.recent(a)).extracting(m -> m.getText()).doesNotContain("question of B");
    }

    @Test
    void emptySessionHasNoHistory() {
        assertThat(history.recent(newSession())).isEmpty();
    }
}
