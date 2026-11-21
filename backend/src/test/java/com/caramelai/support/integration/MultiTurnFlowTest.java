package com.caramelai.support.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.caramelai.support.chat.ChatService;
import com.caramelai.support.persistence.ChatSession;
import com.caramelai.support.persistence.Message;
import com.caramelai.support.persistence.Outcome;
import com.caramelai.support.persistence.Role;
import com.caramelai.support.persistence.Store;
import com.caramelai.support.support.FakeChatModel;
import com.caramelai.support.support.TestChatConfig;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestChatConfig.class)
class MultiTurnFlowTest {

    @Autowired ChatService chat;
    @Autowired FakeChatModel model;
    @Autowired Store store;

    @BeforeEach
    void reset() {
        model.reset();
    }

    private static List<String> conversationTexts(Prompt prompt) {
        return prompt.getInstructions().stream()
                .filter(m -> m.getMessageType() != MessageType.SYSTEM)
                .map(m -> m.getText())
                .toList();
    }

    @Test
    void secondPromptContainsTheFirstTurnInOrder() {
        model.thenReply("It is a rate limit.").thenReply("Wait for Retry-After.");
        UUID s = chat.createSession(null).sessionId();

        chat.sendMessage(s, "I get HTTP 429");
        chat.sendMessage(s, "How do I fix that?");

        assertThat(model.callCount()).isEqualTo(2);
        assertThat(conversationTexts(model.prompts().get(0))).containsExactly("I get HTTP 429");
        assertThat(conversationTexts(model.prompts().get(1)))
                .containsExactly("I get HTTP 429", "It is a rate limit.", "How do I fix that?");
        // system instructions stay first on every call
        assertThat(model.prompts().get(1).getInstructions().getFirst().getMessageType()).isEqualTo(MessageType.SYSTEM);
    }

    @Test
    void longSessionsSendAtMost20ConversationMessages() {
        UUID s = chat.createSession(null).sessionId();
        Instant t = Instant.now().minusSeconds(3600); // seeded messages must be older than the new one
        for (int i = 0; i < 24; i++) { // 12 earlier turns
            boolean isUser = i % 2 == 0;
            store.insert(new Message(UUID.randomUUID(), s, isUser ? Role.USER : Role.ASSISTANT, "old-" + i, isUser ? null : Outcome.ANSWERED,
                    false, null, null, null, null, t.plusSeconds(i)));
        }

        chat.sendMessage(s, "newest question");

        List<String> sent = conversationTexts(model.prompts().getFirst());
        assertThat(sent.size()).isLessThanOrEqualTo(20);
        assertThat(sent.getLast()).isEqualTo("newest question");
        assertThat(sent).doesNotContain("old-0", "old-1", "old-2", "old-3");
    }

    @Test
    void aNewSessionNeverSeesAnotherSessionsContent() {
        model.thenReply("First session answer.");
        UUID first = chat.createSession(null).sessionId();
        chat.sendMessage(first, "private detail from session one");

        UUID second = store.insert(new ChatSession(UUID.randomUUID(), null, Instant.now())).id();
        chat.sendMessage(second, "hello from session two");

        List<String> sent = conversationTexts(model.prompts().getLast());
        assertThat(sent).containsExactly("hello from session two");
    }

    @Test
    void failedTurnsAreNotReplayedToTheModel() {
        model.thenThrow(new RuntimeException("down")).thenReply("ok");
        UUID s = chat.createSession(null).sessionId();

        chat.sendMessage(s, "first try");   // UNAVAILABLE
        chat.sendMessage(s, "second try");

        List<String> sent = conversationTexts(model.prompts().getLast());
        assertThat(sent).contains("first try", "second try");
        assertThat(sent).noneMatch(t -> t.contains("can't answer right now"));
    }
}
