# Caramel AI Support Chat: backend

Spring Boot 4 service that answers Caramel AI support questions through Spring AI and Google AI Studio
(`gemma-4-26b-a4b-it`). See `specs/001-support-chat-assistant/` for the spec, plan, research, and API contract.

## Prerequisites

- JDK 21 and Maven 3.9+
- A Google AI Studio API key (needed to run the app and the live test suite, not for the default tests)

## Configuration and secrets

Secrets live in a repo-root `.env` file that is **never committed** (`.gitignore` lists it). Copy the template:

```bash
cp .env.example .env     # then edit the placeholder values
```

| Variable | Purpose |
|----------|---------|
| `GOOGLE_AI_STUDIO_API_KEY` | Required. The app fails at startup if it is missing, on purpose. |
| `AGENT_API_TOKEN` | Optional. Enables `/api/agent/**` (sent as the `X-Agent-Token` header). Unset means those endpoints return 404. |

Spring Boot does not read `.env` by itself. `application.yml` imports `../.env` and `.env` through
`spring.config.import`, so run Maven from `backend/`. Real environment variables override the file.

Application settings (`app.*` in `src/main/resources/application.yml`):

| Property | Default | Meaning |
|----------|---------|---------|
| `app.prompt.system-role-supported` | `true` | `false` folds the instructions into the first user message |
| `app.knowledge.path` | `classpath:support-content/` | Folder of `*.md` support documents |
| `app.knowledge.max-chars` | `60000` | Startup fails if the documents exceed this size |
| `app.chat.max-message-chars` | `2000` | Longest accepted customer message |
| `app.chat.history-limit` | `20` | Messages of the session sent to the model |
| `app.chat.provider-timeout-seconds` | `30` | Model call timeout; failures become the `UNAVAILABLE` outcome |
| `app.agent.token` | from `AGENT_API_TOKEN` | Agent endpoint secret |
| `app.cors.allowed-origin` | `http://localhost:5173` | The only browser origin allowed to call `/api/**` |

The sample documents in `src/main/resources/support-content/` are **placeholders**. Replace them with approved
Caramel AI content before a pilot.

## Run and test

```bash
cd backend
mvn spring-boot:run          # serves on :8080; database files go to backend/data/ (gitignored)
mvn test                     # default suite: offline, no key, model replaced by FakeChatModel
mvn verify -Plive            # real-model suite: needs a key; skipped automatically when none is found
```

The live suite (`src/test/java/.../live/`) calls the real model: capability spikes, behavior checks, and
prompt-injection attempts. It makes dozens of real requests, so it uses your quota.

## Extending

- **Escalation channel**: implement `EscalationNotifier` (`escalation/`) and register it as a bean in place of
  `LoggingEscalationNotifier`. It receives IDs and the reason only; read the transcript through the repository.
- **Knowledge source**: implement `KnowledgeSource` (`knowledge/`), for example backed by a local vector store, and
  replace `FileKnowledgeSource`. `PromptBuilder` depends only on the interface.

## Notes for maintainers

- Gemma returns reasoning as separate generations flagged `isThought=true`. `ChatService.extractText` skips them.
  Do not switch back to `ChatResponse.getResult()`: it reads the first generation, which is often the thought part.
- Logs carry request ID, outcome, latency, tokens, and error class only, never message text, keys, or the system
  prompt (`LogLeakTest` enforces this). The request ID comes from the logging context, not a key-value pair,
  because the JSON log format rejects duplicate keys.
