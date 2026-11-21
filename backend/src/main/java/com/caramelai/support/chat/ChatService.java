package com.caramelai.support.chat;

import com.caramelai.support.api.InvalidMessageException;
import com.caramelai.support.api.SessionNotFoundException;
import com.caramelai.support.api.dto.AssistantReply;
import com.caramelai.support.api.dto.SessionResponse;
import com.caramelai.support.config.AppProperties;
import com.caramelai.support.config.RequestIdFilter;
import com.caramelai.support.api.dto.EscalationReceipt;
import com.caramelai.support.escalation.EscalationService;
import com.caramelai.support.persistence.EscalationReason;
import com.caramelai.support.persistence.ChatSession;
import com.caramelai.support.persistence.ChatSessionRepository;
import com.caramelai.support.persistence.Message;
import com.caramelai.support.persistence.MessageRepository;
import com.caramelai.support.persistence.Outcome;
import com.caramelai.support.persistence.Role;
import com.caramelai.support.persistence.Store;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

/**
 * Handles one customer message: validate, store, build the prompt, call the model with a timeout, guard the
 * output, store the reply, and log a single structured event. Provider failures never escape: they become the
 * UNAVAILABLE outcome with a friendly message (research R11).
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    static final String UNAVAILABLE_TEXT =
            "I can't answer right now. Please try again in a moment, or ask for a human agent.";
    static final String NO_ANSWER_MARKER = "[[NO_ANSWER]]";
    static final String NO_ANSWER_DEFAULT_TEXT =
            "I don't know the answer to that. I can connect you with a human agent if you like.";
    private static final int MAX_USER_REF = 128;
    /** One retry for transient provider failures; see {@link #callModel}. */
    private static final int MAX_MODEL_ATTEMPTS = 2;
    private static final long RETRY_PAUSE_MS = 700;

    private final ChatSessionRepository sessions;
    private final MessageRepository messages;
    private final Store store;
    private final ChatModel chatModel;
    private final PromptBuilder promptBuilder;
    private final OutputGuard outputGuard;
    private final ConversationHistory history;
    private final EscalationIntentDetector intentDetector;
    private final EscalationService escalations;
    private final AppProperties props;
    private final ExecutorService modelExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public ChatService(
            ChatSessionRepository sessions,
            MessageRepository messages,
            Store store,
            ChatModel chatModel,
            PromptBuilder promptBuilder,
            OutputGuard outputGuard,
            ConversationHistory history,
            EscalationIntentDetector intentDetector,
            EscalationService escalations,
            AppProperties props) {
        this.sessions = sessions;
        this.messages = messages;
        this.store = store;
        this.chatModel = chatModel;
        this.promptBuilder = promptBuilder;
        this.outputGuard = outputGuard;
        this.history = history;
        this.intentDetector = intentDetector;
        this.escalations = escalations;
        this.props = props;
    }

    public SessionResponse createSession(String userRef) {
        if (userRef != null && userRef.length() > MAX_USER_REF) {
            throw new InvalidMessageException("userRef must be at most " + MAX_USER_REF + " characters.");
        }
        ChatSession session = store.insert(new ChatSession(UUID.randomUUID(), userRef, Instant.now()));
        return new SessionResponse(session.id(), session.createdAt());
    }

    public AssistantReply sendMessage(UUID sessionId, String rawText) {
        String text = validate(rawText);
        sessions.findById(sessionId).orElseThrow(() -> new SessionNotFoundException("Session not found."));
        String requestId = MDC.get(RequestIdFilter.MDC_KEY);

        store.insert(userMessage(sessionId, text));

        // A free-text request for a human is handled deterministically: no model call (research R4).
        if (intentDetector.wantsHuman(text)) {
            return escalateWithoutModel(sessionId, requestId);
        }

        // The new user message is already stored, so the history ends with it.
        Prompt prompt = promptBuilder.build(history.recent(sessionId));
        ModelResult result = callModel(prompt);

        Outcome outcome;
        String replyText;
        boolean offer;
        if (result.text == null) {
            outcome = Outcome.UNAVAILABLE;
            replyText = UNAVAILABLE_TEXT;
            offer = true;
        } else {
            OutputGuard.Verdict verdict = outputGuard.check(result.text);
            if (verdict.blocked()) {
                outcome = Outcome.BLOCKED;
                replyText = verdict.text();
                offer = true;
            } else if (startsWithMarker(verdict.text())) {
                outcome = Outcome.NO_ANSWER;
                replyText = stripMarker(verdict.text());
                offer = true;
            } else {
                outcome = Outcome.ANSWERED;
                replyText = verdict.text();
                offer = false;
            }
        }

        Message assistant = store.insert(new Message(
                UUID.randomUUID(), sessionId, Role.ASSISTANT, replyText, outcome, offer, requestId,
                (int) result.latencyMs, result.promptTokens, result.completionTokens, Instant.now()));

        logRequest(sessionId, outcome, result);
        return AssistantReply.of(assistant.id(), replyText, outcome, offer);
    }

    private AssistantReply escalateWithoutModel(UUID sessionId, String requestId) {
        EscalationReceipt receipt = escalations.escalate(sessionId, EscalationReason.CUSTOMER_REQUESTED);
        Message assistant = store.insert(new Message(
                UUID.randomUUID(), sessionId, Role.ASSISTANT, receipt.message(), Outcome.ESCALATED, false, requestId,
                null, null, null, Instant.now()));
        ModelResult none = new ModelResult();
        logRequest(sessionId, Outcome.ESCALATED, none);
        return AssistantReply.escalated(assistant.id(), receipt.message(), receipt.escalationId());
    }

    private static boolean startsWithMarker(String text) {
        return text.stripLeading().startsWith(NO_ANSWER_MARKER);
    }

    private static String stripMarker(String text) {
        String rest = text.stripLeading().substring(NO_ANSWER_MARKER.length()).strip();
        return rest.isEmpty() ? NO_ANSWER_DEFAULT_TEXT : rest;
    }

    private String validate(String rawText) {
        String text = rawText == null ? "" : rawText.strip();
        if (text.isEmpty()) {
            throw new InvalidMessageException("Please type a question.");
        }
        int max = props.chat().maxMessageChars();
        if (text.length() > max) {
            throw new InvalidMessageException("Message is too long (maximum " + max + " characters).");
        }
        return text;
    }

    private Message userMessage(UUID sessionId, String text) {
        return new Message(UUID.randomUUID(), sessionId, Role.USER, text, null, false, MDC.get(RequestIdFilter.MDC_KEY),
                null, null, null, Instant.now());
    }

    /** Outcome of the provider call. {@code text} is null when the call failed or returned nothing usable. */
    private static final class ModelResult {
        String text;
        long latencyMs;
        Integer promptTokens;
        Integer completionTokens;
        String errorClass;
    }

    /**
     * Calls the model within the overall provider timeout. A transient provider failure (for example Google's
     * intermittent HTTP 500 "Internal error encountered") gets one retry after a short pause; a timeout or an
     * interrupt is never retried because the time budget is already spent.
     */
    private ModelResult callModel(Prompt prompt) {
        long start = System.nanoTime();
        long budgetMs = props.chat().providerTimeoutSeconds() * 1000L;
        ModelResult result = null;
        for (int attempt = 1; attempt <= MAX_MODEL_ATTEMPTS; attempt++) {
            result = callModelOnce(prompt, Math.max(1, budgetMs - elapsedMs(start)));
            boolean giveUp = result.text != null
                    || "Timeout".equals(result.errorClass)
                    || "Interrupted".equals(result.errorClass);
            if (giveUp || attempt == MAX_MODEL_ATTEMPTS || elapsedMs(start) + RETRY_PAUSE_MS >= budgetMs) {
                break;
            }
            if (!pause(RETRY_PAUSE_MS)) {
                break;
            }
        }
        result.latencyMs = elapsedMs(start);
        return result;
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    /** Sleeps before a retry. Returns false if interrupted, in which case the caller stops retrying. */
    private static boolean pause(long millis) {
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private ModelResult callModelOnce(Prompt prompt, long timeoutMs) {
        ModelResult result = new ModelResult();
        Future<ChatResponse> future = modelExecutor.submit(() -> chatModel.call(prompt));
        try {
            ChatResponse response = future.get(timeoutMs, TimeUnit.MILLISECONDS);
            String text = extractText(response);
            if (text == null || text.isBlank()) {
                result.errorClass = "EmptyReply";
            } else {
                result.text = text;
            }
            Usage usage = response == null || response.getMetadata() == null ? null : response.getMetadata().getUsage();
            if (usage != null) {
                result.promptTokens = usage.getPromptTokens();
                result.completionTokens = usage.getCompletionTokens();
            }
        } catch (TimeoutException e) {
            future.cancel(true);
            result.errorClass = "Timeout";
        } catch (ExecutionException e) {
            result.errorClass = (e.getCause() != null ? e.getCause() : e).getClass().getSimpleName();
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            result.errorClass = "Interrupted";
        } catch (RuntimeException e) {
            result.errorClass = e.getClass().getSimpleName();
        }
        return result;
    }

    /**
     * Joins the text of the answer generations. Gemma through Google AI Studio returns the model's reasoning as
     * separate generations flagged {@code isThought=true}, usually before the answer. {@code getResult()} would
     * return that first generation, so the reply would be empty or, worse, expose the reasoning (which can quote
     * the system-prompt rules). Thought generations are skipped entirely (found by the live suite, research R3).
     */
    private static String extractText(ChatResponse response) {
        if (response == null || response.getResults() == null) {
            return null;
        }
        StringBuilder text = new StringBuilder();
        for (Generation generation : response.getResults()) {
            if (generation.getOutput() == null || isThought(generation)) {
                continue;
            }
            String part = generation.getOutput().getText();
            if (part != null) {
                text.append(part);
            }
        }
        return text.toString();
    }

    private static boolean isThought(Generation generation) {
        Object flag = generation.getOutput().getMetadata().get("isThought");
        return Boolean.TRUE.equals(flag) || "true".equalsIgnoreCase(String.valueOf(flag));
    }

    /**
     * One structured event per request. Never includes message text, keys, or the system prompt. The request ID is
     * not added here: it is already in the logging context (MDC) and the JSON format rejects duplicate keys.
     */
    private void logRequest(UUID sessionId, Outcome outcome, ModelResult result) {
        var builder = (result.errorClass == null ? log.atInfo() : log.atWarn())
                .setMessage("chat.request")
                .addKeyValue("sessionId", sessionId.toString())
                .addKeyValue("outcome", outcome.name())
                .addKeyValue("latencyMs", result.latencyMs);
        if (result.promptTokens != null) {
            builder = builder.addKeyValue("promptTokens", result.promptTokens);
        }
        if (result.completionTokens != null) {
            builder = builder.addKeyValue("completionTokens", result.completionTokens);
        }
        if (result.errorClass != null) {
            builder = builder.addKeyValue("errorClass", result.errorClass);
        }
        builder.log();
    }
}
