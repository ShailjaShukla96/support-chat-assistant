package com.caramelai.support.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.caramelai.support.api.dto.AssistantReply;
import com.caramelai.support.api.dto.SessionResponse;
import com.caramelai.support.chat.ChatService;
import com.caramelai.support.persistence.Message;
import com.caramelai.support.persistence.MessageRepository;
import com.caramelai.support.persistence.Outcome;
import com.caramelai.support.support.FakeChatModel;
import com.caramelai.support.support.TestChatConfig;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestChatConfig.class)
class ChatFlowTest {

    @Autowired ChatService service;
    @Autowired FakeChatModel model;
    @Autowired MessageRepository messages;

    @BeforeEach
    void reset() {
        model.reset();
    }

    @Test
    void sessionToReplyFlowStoresRowsAndSendsSupportContentToTheModel() {
        model.thenReply("Wait for the Retry-After period, then retry.");
        SessionResponse session = service.createSession(null);

        AssistantReply reply = service.sendMessage(session.sessionId(), "Why do I get HTTP 429?");

        assertThat(reply.outcome()).isEqualTo(Outcome.ANSWERED);
        List<Message> stored = messages.findBySessionIdOrderByCreatedAtAsc(session.sessionId());
        assertThat(stored).extracting(Message::text)
                .containsExactly("Why do I get HTTP 429?", "Wait for the Retry-After period, then retry.");

        String system = model.prompts().getFirst().getInstructions().getFirst().getText();
        // Sample support content shipped with the app must reach the prompt
        assertThat(system).contains("<support_content>").contains("429");
    }
}
