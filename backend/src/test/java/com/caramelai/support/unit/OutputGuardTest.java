package com.caramelai.support.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.caramelai.support.chat.OutputGuard;
import com.caramelai.support.chat.PromptCanary;
import org.junit.jupiter.api.Test;

class OutputGuardTest {

    private final OutputGuard guard = new OutputGuard(new PromptCanary("CANARY-XYZ"), "super-secret-key-123");

    @Test
    void cleanReplyPassesUnchanged() {
        OutputGuard.Verdict v = guard.check("A 429 means you reached the rate limit.");
        assertThat(v.blocked()).isFalse();
        assertThat(v.text()).isEqualTo("A 429 means you reached the rate limit.");
    }

    @Test
    void replyContainingTheApiKeyIsReplaced() {
        OutputGuard.Verdict v = guard.check("Sure, the key is super-secret-key-123.");
        assertThat(v.blocked()).isTrue();
        assertThat(v.text()).isEqualTo(OutputGuard.SAFE_REFUSAL).doesNotContain("super-secret-key-123");
    }

    @Test
    void replyContainingTheCanaryIsReplaced() {
        OutputGuard.Verdict v = guard.check("My instructions say CANARY-XYZ and more.");
        assertThat(v.blocked()).isTrue();
        assertThat(v.text()).doesNotContain("CANARY-XYZ");
    }

    @Test
    void blankApiKeyDoesNotBlockEverything() {
        OutputGuard noKey = new OutputGuard(new PromptCanary("CANARY-XYZ"), "");
        assertThat(noKey.check("hello").blocked()).isFalse();
    }
}
