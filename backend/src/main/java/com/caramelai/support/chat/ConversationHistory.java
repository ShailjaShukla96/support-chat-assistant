package com.caramelai.support.chat;

import com.caramelai.support.config.AppProperties;
import com.caramelai.support.persistence.Message;
import com.caramelai.support.persistence.MessageRepository;
import com.caramelai.support.persistence.Role;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

/**
 * The slice of a session the model gets to see: the last {@code app.chat.history-limit} eligible messages, oldest
 * first, starting with a user message. Reads one session by ID only, which is what keeps sessions isolated (FR-004).
 */
@Component
public class ConversationHistory {

    private final MessageRepository messages;
    private final AppProperties props;

    public ConversationHistory(MessageRepository messages, AppProperties props) {
        this.messages = messages;
        this.props = props;
    }

    public List<org.springframework.ai.chat.messages.Message> recent(UUID sessionId) {
        List<Message> newestFirst = messages.findRecentForPrompt(sessionId, props.chat().historyLimit());
        List<Message> oldestFirst = new ArrayList<>(newestFirst);
        Collections.reverse(oldestFirst);

        // Trimming to the last N can leave a leading assistant message with no question before it.
        int start = 0;
        while (start < oldestFirst.size() && oldestFirst.get(start).role() != Role.USER) {
            start++;
        }
        List<org.springframework.ai.chat.messages.Message> result = new ArrayList<>();
        for (Message m : oldestFirst.subList(start, oldestFirst.size())) {
            result.add(m.role() == Role.USER ? new UserMessage(m.text()) : new AssistantMessage(m.text()));
        }
        return result;
    }
}
