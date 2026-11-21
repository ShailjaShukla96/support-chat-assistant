package com.caramelai.support.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.caramelai.support.chat.PromptBuilder;
import com.caramelai.support.chat.PromptCanary;
import com.caramelai.support.config.AppProperties;
import com.caramelai.support.knowledge.SupportContentItem;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;

class PromptBuilderTest {

    private static final PromptCanary CANARY = new PromptCanary("CANARY-TEST-TOKEN");
    private static final List<SupportContentItem> ITEMS = List.of(
            new SupportContentItem("rate-limits", "Rate limits", "A 429 means you hit the rate limit."),
            new SupportContentItem("latency", "Latency", "Latency depends on the model size."));

    private PromptBuilder builder(boolean systemRoleSupported) {
        AppProperties defaults = AppProperties.defaults();
        AppProperties props = new AppProperties(
                new AppProperties.Prompt(systemRoleSupported),
                defaults.knowledge(), defaults.chat(), defaults.agent(), defaults.cors());
        return new PromptBuilder(() -> ITEMS, CANARY, props);
    }

    @Test
    void systemMessageHoldsEverySupportItemAndTheCanary() {
        Prompt prompt = builder(true).build(List.of(new UserMessage("Why 429?")));

        Message system = prompt.getInstructions().getFirst();
        assertThat(system.getMessageType()).isEqualTo(MessageType.SYSTEM);
        assertThat(system.getText())
                .contains("<support_content>")
                .contains("</support_content>")
                .contains("Rate limits")
                .contains("A 429 means you hit the rate limit.")
                .contains("Latency depends on the model size.")
                .contains("CANARY-TEST-TOKEN")
                .doesNotContain("{{");
    }

    @Test
    void customerMessageIsLast() {
        Prompt prompt = builder(true).build(List.of(
                new UserMessage("first"), new AssistantMessage("answer"), new UserMessage("Why 429?")));

        List<Message> messages = prompt.getInstructions();
        assertThat(messages.getLast().getMessageType()).isEqualTo(MessageType.USER);
        assertThat(messages.getLast().getText()).isEqualTo("Why 429?");
        assertThat(messages).hasSize(4);
    }

    @Test
    void foldsInstructionsIntoFirstUserMessageWhenSystemRoleUnsupported() {
        Prompt prompt = builder(false).build(List.of(new UserMessage("Why 429?")));

        assertThat(prompt.getInstructions()).hasSize(1);
        Message only = prompt.getInstructions().getFirst();
        assertThat(only.getMessageType()).isEqualTo(MessageType.USER);
        assertThat(only.getText())
                .contains("A 429 means you hit the rate limit.")
                .contains("CANARY-TEST-TOKEN")
                .endsWith("Why 429?");
    }

    @Test
    void foldedModeKeepsLaterMessagesUntouched() {
        Prompt prompt = builder(false).build(List.of(
                new UserMessage("first"), new AssistantMessage("answer"), new UserMessage("second")));

        assertThat(prompt.getInstructions()).hasSize(3);
        assertThat(prompt.getInstructions().get(1).getText()).isEqualTo("answer");
        assertThat(prompt.getInstructions().get(2).getText()).isEqualTo("second");
        assertThat(prompt.getInstructions().stream()
                        .filter(m -> m.getMessageType() == MessageType.SYSTEM)
                        .count())
                .isZero();
    }
}
