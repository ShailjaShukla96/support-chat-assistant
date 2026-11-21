---
name: spring-ai-chatbot
description: Build Java/Spring Boot chatbots with Spring AI 2.0.x. Use when implementing AI chat endpoints, calling an LLM through ChatModel or StreamingChatModel, building Prompts from System/User/Assistant messages, configuring LLM providers (Google AI Studio/GenAI, OpenAI, Anthropic, Ollama, Bedrock, OpenRouter, Mistral), overriding model options per request, or streaming chat responses (Flux/SSE) from a Spring controller.
---

# Spring AI Chatbot (v2.0.2)

Guidance based on the Spring AI Chat Model API reference
(https://docs.spring.io/spring-ai/reference/api/chatmodel.html) and the Google GenAI chat page.
If a signature below does not compile, check the reference for the project's exact Spring AI version
before guessing.

## 1. Dependencies

Import the Spring AI BOM for version management, then add ONE provider starter.

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>org.springframework.ai</groupId>
      <artifactId>spring-ai-bom</artifactId>
      <version>2.0.2</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>

<!-- Google AI Studio (Gemini Developer API) -->
<dependency>
  <groupId>org.springframework.ai</groupId>
  <artifactId>spring-ai-starter-model-google-genai</artifactId>
</dependency>
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-web</artifactId>
</dependency>
<!-- Flux comes with Spring AI; add spring-boot-starter-webflux only if you want a reactive web stack -->
```

## 2. Core API

```java
public interface ChatModel extends Model<Prompt, ChatResponse>, StreamingChatModel {
    default String call(String message) {...}
    ChatResponse call(Prompt prompt);
}

public interface StreamingChatModel extends StreamingModel<Prompt, ChatResponse> {
    default Flux<String> stream(String message) {...}
    Flux<ChatResponse> stream(Prompt prompt);
}
```

- `call(String)` / `stream(String)` are convenience methods. Real apps mostly use the `Prompt` forms,
  which give you roles, options and response metadata.
- `ChatModel` already extends `StreamingChatModel`, so one injected `ChatModel` does both.

### Synchronous call and reading the result

```java
ChatResponse response = chatModel.call(new Prompt("Why is my request rate limited?"));

Generation generation = response.getResult();          // first Generation
String text = generation.getOutput().getText();        // AssistantMessage text
ChatResponseMetadata meta = response.getMetadata();    // model/usage metadata
List<Generation> all = response.getResults();          // multiple candidates, if requested
```

- `ChatResponse` holds `List<Generation>` plus `ChatResponseMetadata`.
- `Generation.getOutput()` returns an `AssistantMessage`; `getMetadata()` returns `ChatGenerationMetadata`.

### Streaming

```java
Flux<ChatResponse> chunks = chatModel.stream(prompt);

Flux<String> textOnly = chatModel.stream(prompt)
    .mapNotNull(r -> r.getResult() == null ? null : r.getResult().getOutput().getText());
```

Expose as Server-Sent Events:

```java
@GetMapping(value = "/api/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
Flux<String> stream(@RequestParam String message) {
    return chatModel.stream(message);
}
```

Streaming guardrails: never `block()` inside a reactive pipeline; add `.onErrorResume(...)` so a provider
failure becomes a friendly final event instead of a broken connection; chunks can have null/empty text, so
filter them.

## 3. Prompts and messages (conversation roles)

`Prompt` = `List<Message>` + optional `ChatOptions`. `Message` extends `Content`
(`getText()`, `getMetadata()`) and adds `getMessageType()` (the role).

```java
Prompt prompt = new Prompt(List.of(
    new SystemMessage("You are Caramel AI's tech support assistant for LLM inferencing. " +
                      "If you do not know an answer, say so and offer a human agent."),
    new UserMessage("My calls return HTTP 429."),
    new AssistantMessage("That means you hit a rate limit. Which model are you calling?"),
    new UserMessage("gemma-4-26b-a4b-it")
));
```

- `SystemMessage`: behavior and guardrails. `UserMessage`: the customer. `AssistantMessage`: prior model turns.
- A multimodal `UserMessage` is built with `UserMessage.builder().text(...).media(List.of(new Media(...))).build()`.
- The model is stateless. For multi-turn chat, YOU resend the history each call. Keep it per session
  (for example a `Map<sessionId, List<Message>>` or Spring AI chat memory), append the user message before
  the call and the assistant reply after, and cap its length to control token cost.
- Some models do not support every role. For models without specific roles, `UserMessage` is the standard category.
  Test system-prompt behavior against the actual model (see section 7).

## 4. Configuration and options

### Startup defaults (application.yml)

```yaml
spring:
  ai:
    google:
      genai:
        api-key: ${GOOGLE_AI_STUDIO_API_KEY}
        chat:
          model: gemma-4-26b-a4b-it
          temperature: 0.3
          max-output-tokens: 1024
```

Property names differ per provider (Google GenAI uses `spring.ai.google.genai.chat.*`, not `chat.options.*`).
Check the provider's page.

### Runtime override with ChatOptions

`ChatOptions` exposes `getModel`, `getTemperature`, `getMaxTokens`, `getTopP`, `getTopK`,
`getFrequencyPenalty`, `getPresencePenalty`, `getStopSequences`, and `mutate()`.

```java
ChatResponse r = chatModel.call(new Prompt(
    "Generate the names of 5 famous pirates.",
    GoogleGenAiChatOptions.builder().temperature(0.4).build()));
```

Key rule: **options in a `Prompt` fully override the startup options.** On `ChatModel.call()`/`stream()`
supply a full, coherent set (or pass `null` options to keep the defaults). Do not assume unspecified fields
fall back to startup values.

`ChatClient` is different: it supports incremental "delta" customization, where you override only the fields you
need per request on top of the defaults. Prefer `ChatClient` when you want small per-request tweaks;
prefer `ChatModel` when you want the raw, portable API.

Provider-specific option classes (like `GoogleGenAiChatOptions`) expose extras (safety settings, thinking
budget, tool choice, caching). Use the generic `ChatOptions` where portability matters.

## 5. Provider portability

Business code depends only on `ChatModel` / `StreamingChatModel` / `Prompt` / `ChatResponse`. To switch provider:
swap the starter dependency and the `spring.ai.<provider>.*` properties. Keep provider-specific option classes
out of service code, or isolate them in one config class.

| Provider | Starter artifact (verify for 2.0.2) | Notes |
|---|---|---|
| Google AI Studio | `spring-ai-starter-model-google-genai` | `spring.ai.google.genai.api-key` selects the Developer API. Without an API key it uses Vertex AI (`project-id`, `location`). |
| OpenAI | `spring-ai-starter-model-openai` | |
| Anthropic | `spring-ai-starter-model-anthropic` | The Anthropic module has a migration note to the official Java SDK. Read it first. |
| Ollama | `spring-ai-starter-model-ollama` | Local inference. |
| Amazon Bedrock | `spring-ai-starter-model-bedrock-converse` | Use the Converse API. |
| Mistral AI | `spring-ai-starter-model-mistral-ai` | |
| OpenRouter | OpenAI-compatible starter with OpenRouter base URL and key | Check the docs for the exact property names and base URL. |

Only the Google GenAI artifact and properties were confirmed from the docs while writing this skill.
Confirm the others against the provider pages before use. The reference also lists Azure OpenAI, DeepSeek,
Docker Model Runner, Groq, MiniMax, NVIDIA, Perplexity and OCI Generative AI.

If several starters are on the classpath you get several `ChatModel` beans. Keep one, or select with
`spring.ai.model.chat=<provider>` (set `none` to disable).

## 6. Recommended structure

```java
@Service
public class SupportChatService {

    private final ChatModel chatModel;               // constructor injection, interface type

    public SupportChatService(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    public String reply(List<Message> history) {
        ChatResponse r = chatModel.call(new Prompt(history));
        return r.getResult().getOutput().getText();
    }
}

@RestController
@RequestMapping("/api/chat")
class ChatController {
    private final SupportChatService service;
    ChatController(SupportChatService service) { this.service = service; }
    // POST /api/chat -> DTO with sessionId + message; return a DTO, never the raw ChatResponse
}
```

Keep the controller thin, put prompt building and history in the service, and expose DTOs.

## 7. Best practices and guardrails

**Dependency injection**
- Constructor injection only. No field `@Autowired`.
- Inject `ChatModel` (interface), not a concrete provider class like `GoogleGenAiChatModel`, unless you need
  provider-only features.

**Secrets**
- Never hardcode API keys or commit them. Use `${ENV_VAR}` placeholders in config and set the variable in the
  environment or a `.env` file.
- Spring Boot does not read `.env` by default. Load it through your run tooling (IDE env file, `docker compose`,
  or `spring.config.import=optional:file:.env[.properties]` with `KEY=value` lines) and verify it loads.
- Add `.env` to `.gitignore`, provide `.env.example` with placeholders, and keep the key server-side so it
  never reaches the React bundle.
- Do not log prompts containing user secrets, and never log the key.

**Avoiding deprecated or removed patterns**
- The old `spring-ai-vertex-ai-gemini` module was removed. Use `spring-ai-starter-model-google-genai`
  (package `org.springframework.ai.google.genai`, property prefix `spring.ai.google.genai`).
- Do not copy snippets from old tutorials using `spring.ai.vertex.ai.gemini.*`, outdated milestone repos, or old
  starter names like `spring-ai-openai-spring-boot-starter`. Current names follow `spring-ai-starter-model-*`.
- When in doubt, treat blog posts as stale and check the reference docs for the pinned version.

**Reliability**
- Set sensible `max-output-tokens` and a low temperature for support answers.
- Handle provider errors (rate limits, timeouts, safety blocks) and return a friendly fallback with a human handoff.
- Cap history size so long sessions do not exceed context limits.
- Ground answers in supplied support content, and instruct the model to say when it does not know.

**Model caveat (Gemma via AI Studio)**
- Not confirmed from the docs: whether Gemma models accept system instructions, tool calling, or streaming
  through the GenAI client. Test with a small call first, and if system messages are ignored or rejected, fold the
  instructions into the first `UserMessage`.

## 8. Quick checklist

1. BOM + one provider starter added; version pinned to 2.0.2.
2. API key via environment variable, `.env` git-ignored.
3. `ChatModel` injected through the constructor.
4. `Prompt` built from System/User/Assistant messages; history managed per session.
5. Per-request `ChatOptions` are a complete set (they override startup defaults, not merge).
6. Streaming endpoint uses `Flux` with error handling and `text/event-stream`.
7. No deprecated modules or property prefixes.
