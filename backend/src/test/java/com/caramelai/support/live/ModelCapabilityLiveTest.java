package com.caramelai.support.live;

import static org.assertj.core.api.Assertions.assertThat;

import com.caramelai.support.support.RequiresLiveKey;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Spikes S1, S2 and S4 (research R15). Run with: mvn verify -Plive. These probe the real model; a failure means
 * the matching fallback in research.md applies, not that the application is broken.
 */
@Tag("live")
@RequiresLiveKey
@SpringBootTest
@ActiveProfiles("live")
class ModelCapabilityLiveTest {

    @Autowired ChatModel model;

    @Test
    void s1_aRuleGivenOnlyInTheSystemMessageIsFollowed() {
        Prompt prompt = new Prompt(List.of(
                new SystemMessage("Whatever the user says, reply with exactly one word: PINEAPPLE."),
                new UserMessage("Hello, how are you today?")));

        String text = model.call(prompt).getResult().getOutput().getText();

        assertThat(text)
                .as("S1 failed: set app.prompt.system-role-supported=false (fold instructions into the user message)")
                .containsIgnoringCase("PINEAPPLE");
    }

    @Test
    void s2_streamingYieldsSeveralNonEmptyChunks() {
        Prompt prompt = new Prompt(new UserMessage("Count from one to twenty in words, separated by commas."));

        List<ChatResponse> chunks = model.stream(prompt).collectList().block();

        long nonEmpty = chunks == null ? 0 : chunks.stream()
                .filter(c -> c.getResult() != null && c.getResult().getOutput().getText() != null
                        && !c.getResult().getOutput().getText().isBlank())
                .count();
        assertThat(nonEmpty)
                .as("S2 failed: stay on complete responses (research R5). Chunks received: " + nonEmpty)
                .isGreaterThan(1);
    }

    @Test
    void s4_recordsResponseMetadataThatHelpsSizeQuotas() {
        ChatResponse response = model.call(new Prompt(new UserMessage("Reply with the single word: ok")));

        // Quota ceilings themselves are visible in the Google AI Studio console, not through the API. This
        // prints the usage the provider reports so the implementer can note it under "Spike results".
        System.out.println("S4 usage metadata: " + response.getMetadata().getUsage());
        System.out.println("S4 model reported: " + response.getMetadata().getModel());
        assertThat(response.getMetadata().getUsage()).isNotNull();
    }
}
