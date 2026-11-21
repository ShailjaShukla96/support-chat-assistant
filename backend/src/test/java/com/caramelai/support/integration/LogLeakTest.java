package com.caramelai.support.integration;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.caramelai.support.chat.ChatService;
import com.caramelai.support.chat.PromptCanary;
import com.caramelai.support.escalation.EscalationService;
import com.caramelai.support.persistence.EscalationReason;
import com.caramelai.support.support.FakeChatModel;
import com.caramelai.support.support.TestChatConfig;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** Constitution V: logs may carry request metadata, never message text, secrets, or the system prompt. */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestChatConfig.class)
class LogLeakTest {

    @Autowired ChatService chat;
    @Autowired EscalationService escalations;
    @Autowired FakeChatModel model;
    @Autowired PromptCanary canary;

    private Logger root;
    private ListAppender<ILoggingEvent> appender;
    private Level previousLevel;

    @BeforeEach
    void capture() {
        model.reset();
        root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        previousLevel = root.getLevel();
        root.setLevel(Level.DEBUG); // capture as much as could ever be logged
        appender = new ListAppender<>();
        appender.start();
        root.addAppender(appender);
    }

    @AfterEach
    void release() {
        root.detachAppender(appender);
        root.setLevel(previousLevel);
    }

    private String everythingLogged() {
        return appender.list.stream()
                .map(e -> e.getLoggerName() + " " + e.getFormattedMessage() + " "
                        + (e.getKeyValuePairs() == null ? "" : e.getKeyValuePairs().stream()
                                .map(p -> p.key + "=" + p.value).collect(Collectors.joining(",")))
                        + " " + e.getMDCPropertyMap()
                        + " " + (e.getThrowableProxy() == null ? "" : e.getThrowableProxy().getMessage()))
                .collect(Collectors.joining("\n"));
    }

    @Test
    void chatEscalationAndProviderFailureLogNoCustomerTextSecretsOrPrompt() {
        model.thenReply("UNIQUE-REPLY-BODY-7731")
                .thenThrow(new IllegalStateException("UNIQUE-PROVIDER-ERROR-9912 key=test-key-not-real"))
                .thenReply("A reply that leaks test-key-not-real");
        UUID s = chat.createSession("host-user-ref-5521").sessionId();

        chat.sendMessage(s, "UNIQUE-QUESTION-1 about my invoice");
        chat.sendMessage(s, "UNIQUE-QUESTION-2 provider fails");
        chat.sendMessage(s, "UNIQUE-QUESTION-3 key leak attempt");
        chat.sendMessage(s, "UNIQUE-QUESTION-4 I want to talk to a person");
        escalations.escalate(s, EscalationReason.ASSISTANT_UNSURE);

        String logs = everythingLogged();
        assertThat(logs).isNotBlank();
        assertThat(logs)
                .doesNotContain("UNIQUE-QUESTION")
                .doesNotContain("UNIQUE-REPLY-BODY")
                .doesNotContain("UNIQUE-PROVIDER-ERROR")
                .doesNotContain("test-key-not-real")
                .doesNotContain("host-user-ref-5521")
                .doesNotContain(canary.value())
                .doesNotContain("<support_content>")
                .doesNotContain("You are the Caramel AI technical support assistant")
                .doesNotContain("Retry-After"); // sample support content text
    }
}
