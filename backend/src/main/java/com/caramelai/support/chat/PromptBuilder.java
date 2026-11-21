package com.caramelai.support.chat;

import com.caramelai.support.config.AppProperties;
import com.caramelai.support.knowledge.KnowledgeSource;
import com.caramelai.support.knowledge.SupportContentItem;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Builds the model prompt: the system instructions (with support content and canary) followed by the
 * conversation. When {@code app.prompt.system-role-supported=false} the instructions are folded into the first
 * user message instead (research R3).
 */
@Component
public class PromptBuilder {

    private static final String FOLD_SEPARATOR = "\n\n---\n\nCustomer message:\n";

    private final String instructions;
    private final boolean systemRoleSupported;

    @Autowired
    public PromptBuilder(KnowledgeSource knowledge, PromptCanary canary, AppProperties props) {
        String template = loadTemplate();
        String content = knowledge.items().stream()
                .map(PromptBuilder::render)
                .collect(Collectors.joining("\n\n"));
        this.instructions = template
                .replace("{{CANARY}}", canary.value())
                .replace("{{SUPPORT_CONTENT}}", content);
        this.systemRoleSupported = props.prompt().systemRoleSupported();
    }

    /** @param conversation user and assistant messages in order, ending with the customer's new message */
    public Prompt build(List<Message> conversation) {
        List<Message> messages = new ArrayList<>();
        if (systemRoleSupported) {
            messages.add(new SystemMessage(instructions));
            messages.addAll(conversation);
        } else {
            boolean folded = false;
            for (Message m : conversation) {
                if (!folded) {
                    if (m.getMessageType() != MessageType.USER) {
                        continue; // the prompt must start with a user message
                    }
                    messages.add(new UserMessage(instructions + FOLD_SEPARATOR + m.getText()));
                    folded = true;
                } else {
                    messages.add(m);
                }
            }
        }
        return new Prompt(messages);
    }

    private static String render(SupportContentItem item) {
        return "## [" + item.id() + "] " + item.title() + "\n" + item.text().strip();
    }

    private static String loadTemplate() {
        try {
            return new ClassPathResource("prompts/system-prompt.txt").getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read prompts/system-prompt.txt", e);
        }
    }
}
