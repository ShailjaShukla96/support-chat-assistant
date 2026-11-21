package com.caramelai.support.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.caramelai.support.chat.EscalationIntentDetector;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EscalationIntentDetectorTest {

    private final EscalationIntentDetector detector = new EscalationIntentDetector();

    @ParameterizedTest
    @ValueSource(strings = {
        "talk to a human",
        "I want to speak to a person",
        "get me a real person",
        "human agent please",
        "connect me to a support agent",
        "I need a representative",
        "Can I speak with someone?",
        "TALK TO A HUMAN!!",
        "please transfer me to a live agent",
        "I want a human",
        "customer support agent please",
    })
    void detectsRequestsForAHuman(String text) {
        assertThat(detector.wantsHuman(text)).as(text).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "is the output human-readable?",
        "how do human labelers rate responses?",
        "what does the agent parameter do?",
        "my agent framework returns 429",
        "I need an agent framework for tool calling",
        "I need a human-readable error format",
        "why do I get HTTP 429?",
        "how do I talk to the API from Python?",
        "Which model is best for chat?",
        "",
    })
    void ignoresOrdinarySupportQuestions(String text) {
        assertThat(detector.wantsHuman(text)).as(text).isFalse();
    }

    @org.junit.jupiter.api.Test
    void nullIsNotARequest() {
        assertThat(detector.wantsHuman(null)).isFalse();
    }
}
