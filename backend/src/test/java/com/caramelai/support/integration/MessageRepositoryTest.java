package com.caramelai.support.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.caramelai.support.persistence.ChatSession;
import com.caramelai.support.persistence.ChatSessionRepository;
import com.caramelai.support.persistence.Message;
import com.caramelai.support.persistence.MessageRepository;
import com.caramelai.support.persistence.Role;
import com.caramelai.support.persistence.Store;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJdbcTest
@ActiveProfiles("test")
@Import(Store.class)
class MessageRepositoryTest {

    @Autowired Store store;
    @Autowired ChatSessionRepository sessions;
    @Autowired MessageRepository messages;

    private ChatSession newSession() {
        return store.insert(new ChatSession(UUID.randomUUID(), null, Instant.now()));
    }

    private Message userMessage(UUID sessionId, String text, Instant at) {
        return store.insert(new Message(UUID.randomUUID(), sessionId, Role.USER, text, null, false, null, null, null, null, at));
    }

    @Test
    void messagesReturnInCreatedAtOrderAndNeverFromAnotherSession() {
        ChatSession a = newSession();
        ChatSession b = newSession();
        Instant t = Instant.parse("2026-11-14T10:00:00Z");
        userMessage(a.id(), "second", t.plusSeconds(2));
        userMessage(a.id(), "first", t.plusSeconds(1));
        userMessage(b.id(), "other session", t.plusSeconds(3));

        var result = messages.findBySessionIdOrderByCreatedAtAsc(a.id());

        assertThat(result).extracting(Message::text).containsExactly("first", "second");
        assertThat(sessions.findById(b.id())).isPresent();
    }
}
