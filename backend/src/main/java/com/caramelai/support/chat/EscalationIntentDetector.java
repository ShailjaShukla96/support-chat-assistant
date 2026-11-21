package com.caramelai.support.chat;

import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Detects free-text requests for a human agent with phrase patterns (research R4). Phrase-based on purpose:
 * single words such as "agent" or "human" appear in ordinary support questions ("agent framework",
 * "human-readable"), so every pattern needs a verb or a qualifier, and the matched noun must not continue into a
 * hyphenated or longer word.
 */
@Component
public class EscalationIntentDetector {

    private static final String END = "(?![\\w-])";
    private static final String PERSON = "(?:human|person|people|agent|representative|rep|someone|somebody|operator|staff)";

    private static final List<Pattern> PATTERNS = List.of(
            // "talk to a human", "speak with someone", "connect me to a support agent", "transfer me to a live agent"
            p("\\b(?:talk|speak|chat|connect|transfer|escalate)\\w*\\s+(?:me\\s+)?(?:to|with)\\s+(?:a|an|the|your|some)?\\s*"
                    + "(?:real\\s+|live\\s+|human\\s+|support\\s+|customer\\s+(?:support\\s+|service\\s+)?)?" + PERSON + END),
            // "I want a human", "get me a real person", "I need a representative" (bare "agent" is excluded)
            p("\\b(?:get|give|need|want|find)\\s+(?:me\\s+)?(?:a|an|the)?\\s*(?:real\\s+|live\\s+|human\\s+)?"
                    + "(?:human|person|representative)" + END),
            // "human agent", "live agent", "support agent", "customer support agent"
            p("\\b(?:human|live|real|support|customer\\s+support|customer\\s+service)\\s+(?:agent|representative|rep)" + END),
            // "human support", "live help"
            p("\\b(?:human|live)\\s+(?:support|help|assistance)" + END));

    private static Pattern p(String regex) {
        return Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
    }

    public boolean wantsHuman(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return PATTERNS.stream().anyMatch(pattern -> pattern.matcher(text).find());
    }
}
