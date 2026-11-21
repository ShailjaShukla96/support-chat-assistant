package com.caramelai.support.chat;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * A random token placed in the system prompt. If it ever appears in a reply, the system prompt leaked and
 * {@link OutputGuard} blocks the reply.
 */
@Component
public class PromptCanary {

    private final String value;

    @Autowired
    public PromptCanary() {
        this("CANARY-" + UUID.randomUUID());
    }

    public PromptCanary(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
