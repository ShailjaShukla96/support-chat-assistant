package com.caramelai.support.chat;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Deterministic last line of defense (Constitution II): a reply that contains the API key or the prompt canary
 * is replaced with a fixed refusal before it is stored or returned.
 */
@Component
public class OutputGuard {

    public static final String SAFE_REFUSAL =
            "I can't share that. I can help with questions about using Caramel AI, or connect you with a human agent.";

    public record Verdict(boolean blocked, String text) {}

    private final PromptCanary canary;
    private final String apiKey;

    @Autowired
    public OutputGuard(PromptCanary canary, @Value("${spring.ai.google.genai.api-key:}") String apiKey) {
        this.canary = canary;
        this.apiKey = apiKey == null ? "" : apiKey;
    }

    public Verdict check(String reply) {
        boolean leaksKey = !apiKey.isBlank() && reply.contains(apiKey);
        boolean leaksCanary = reply.contains(canary.value());
        if (leaksKey || leaksCanary) {
            return new Verdict(true, SAFE_REFUSAL);
        }
        return new Verdict(false, reply);
    }
}
