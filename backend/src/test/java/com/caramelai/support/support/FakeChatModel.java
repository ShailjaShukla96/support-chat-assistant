package com.caramelai.support.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

/**
 * Scripted {@link ChatModel} for offline tests. Each call consumes the next step from the script; when the
 * script is empty it returns a default reply. Records every prompt it receives.
 */
public class FakeChatModel implements ChatModel {

    private final List<Supplier<ChatResponse>> script = new ArrayList<>();
    private final List<Prompt> prompts = new CopyOnWriteArrayList<>();
    private volatile long delayMillis = 0;

    public static ChatResponse reply(String text) {
        return ChatResponse.builder()
                .generations(List.of(new Generation(new AssistantMessage(text))))
                .metadata(ChatResponseMetadata.builder().usage(new DefaultUsage(11, 7)).build())
                .build();
    }

    /** Mimics Gemma through Google AI Studio: a separate generation flagged isThought=true, then the answer. */
    public static ChatResponse replyWithThought(String thought, String answer) {
        AssistantMessage thoughtMessage = AssistantMessage.builder()
                .content(thought)
                .properties(Map.of("isThought", true))
                .build();
        AssistantMessage answerMessage = AssistantMessage.builder()
                .content(answer)
                .properties(Map.of("isThought", false))
                .build();
        return ChatResponse.builder()
                .generations(List.of(new Generation(thoughtMessage), new Generation(answerMessage)))
                .metadata(ChatResponseMetadata.builder().usage(new DefaultUsage(11, 7)).build())
                .build();
    }

    public FakeChatModel thenReplyWithThought(String thought, String answer) {
        script.add(() -> replyWithThought(thought, answer));
        return this;
    }

    public FakeChatModel thenReply(String text) {
        script.add(() -> reply(text));
        return this;
    }

    public FakeChatModel thenThrow(RuntimeException e) {
        script.add(() -> {
            throw e;
        });
        return this;
    }

    public FakeChatModel delay(long millis) {
        this.delayMillis = millis;
        return this;
    }

    public synchronized FakeChatModel reset() {
        script.clear();
        prompts.clear();
        delayMillis = 0;
        return this;
    }

    public List<Prompt> prompts() {
        return prompts;
    }

    public int callCount() {
        return prompts.size();
    }

    @Override
    public synchronized ChatResponse call(Prompt prompt) {
        prompts.add(prompt);
        if (delayMillis > 0) {
            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        if (script.isEmpty()) {
            return reply("Default fake reply.");
        }
        return script.removeFirst().get();
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        return Flux.just(call(prompt));
    }
}
