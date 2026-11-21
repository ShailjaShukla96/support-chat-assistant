# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

Caramel AI's technical-support chat assistant. Customers ask about LLM inferencing (API errors, rate limits,
latency, model selection, token usage); the assistant answers only from support content and escalates to a
human when it cannot. A Spring Boot 4 / Spring AI backend calls `gemma-4-26b-a4b-it` through Google AI Studio;
a React + TypeScript (Vite) frontend talks to it over HTTP. The v1 product spec is [PRD.md](PRD.md); the
Spec Kit spec for v1 is `specs/001-support-chat-assistant/`.

## Authentication and accounts (in scope for the follow-up)

- **v1 is anonymous.** A chat is identified only by a session ID. The only credential is the shared
  `X-Agent-Token` (`AgentTokenFilter`) guarding `/api/agent/**`, which the follow-up replaces.
- **The follow-up ([002-prd-user-auth.md](002-prd-user-auth.md)) brings in:** customer sign-up, sign-in,
  sign-out, and password reset; persisted history for signed-in customers; linking an anonymous session to an
  account at sign-in; per-agent accounts with roles `CUSTOMER`, `AGENT`, `ADMIN`; session management and rate
  limiting on auth endpoints. Anonymous chat must keep working.
- Governed by constitution v2.0.0 (`.specify/memory/constitution.md`), Principle VI and the revised
  session-memory rule. Auth work starts with `/speckit-specify`, then plan, tasks, implement. Do not add auth
  code before its spec exists.

## Commands

Prerequisites: JDK 21, Maven 3.9+, Node/npm, and a repo-root `.env` (copy `.env.example`). The backend
requires `GOOGLE_AI_STUDIO_API_KEY` and fails at startup without it, on purpose.

**Whole stack:** `./run.sh` starts the backend (first free port in 8080-8099), the frontend on 5173 (fixed,
because the backend CORS allow-list expects that origin), and opens Chrome.

**Backend** (run Maven from `backend/`; `application.yml` imports `../.env` relative to that directory):

```bash
mvn spring-boot:run                      # http://localhost:8080
mvn test                                 # default suite: offline, FakeChatModel, no key needed
mvn -Dtest=ChatServiceTest test          # one test class (verified)
mvn -Dtest=ChatServiceTest#someMethod test   # one method (standard Surefire syntax)
mvn verify -Plive                        # real-model suite (live/*LiveTest.java); needs key, uses quota
```

**Frontend** (run from `frontend/`; install once with `npm ci`):

```bash
npm run dev                              # Vite on :5173; proxies /api to the backend
npm test                                 # vitest run
npx vitest run tests/rating.test.tsx     # one file (verified)
npx vitest run -t "name fragment"        # one test by name
npm run lint                             # eslint src tests
npm run build                            # tsc --noEmit && vite build
```

## Backend architecture

Packages live under `backend/src/main/java/com/caramelai/support/`. The request path is the part to understand:

1. `api/MessageController` → `chat/ChatService.sendMessage`. Validates the text (max `app.chat.max-message-chars`),
   stores the user message, and runs `EscalationIntentDetector` (regex). A request for a human escalates
   immediately with no model call.
2. `chat/PromptBuilder` builds the prompt: `prompts/system-prompt.txt` with `{{CANARY}}` and `{{SUPPORT_CONTENT}}`
   filled in, followed by the last `app.chat.history-limit` messages from `ConversationHistory`.
3. The model is called on a virtual-thread executor with an overall timeout. One retry covers transient
   provider errors; timeouts are not retried. Provider failure becomes outcome `UNAVAILABLE` with fixed friendly
   text. Raw errors never reach the customer.
4. `ChatService.extractText` joins only the answer generations. Gemma returns its reasoning as generations flagged
   `isThought=true`; `ChatResponse.getResult()` must not be used because it can return the reasoning.
5. `chat/OutputGuard` replaces any reply containing the API key or the prompt canary with a fixed refusal.
6. A reply starting with `[[NO_ANSWER]]` becomes outcome `NO_ANSWER`, the marker is stripped, and escalation is
   offered. Outcomes: `ANSWERED`, `NO_ANSWER`, `BLOCKED`, `UNAVAILABLE`, `ESCALATED`.
7. The assistant message is stored and one structured log event is written. The event carries IDs, outcome,
   latency, token counts, and error class, never message text, keys, or the prompt.

Other pieces that span files:

- **Knowledge:** `knowledge/FileKnowledgeSource` loads `resources/support-content/*.md` at startup and fails if
  they exceed `app.knowledge.max-chars`. The content goes into the system prompt; there is no vector store. The
  documents are placeholders. Swap the source by implementing `KnowledgeSource`.
- **Escalation:** `EscalationService` stores an immutable transcript snapshot in `escalation_request` and calls an
  `EscalationNotifier`. The default is `LoggingEscalationNotifier`; a real channel replaces it as a bean.
- **Persistence:** Spring Data JDBC on H2 (file DB in `backend/data/`). `resources/schema.sql` is the schema, with
  no migration tool. Entities use application-generated UUIDs, so write through `persistence/Store`
  (`JdbcAggregateTemplate`), not `CrudRepository.save`, which would treat them as updates.
- **Agent API:** `/api/agent/**` is behind `AgentTokenFilter` and returns 404 when no token is configured.
  Token comparison is constant-time.
- **Config:** `AppProperties` binds the `app.*` keys. `CorsConfig` allows only `app.cors.allowed-origin` for
  `/api/**`. `RequestIdFilter` puts the request ID in the logging context (MDC), not as a log key-value pair,
  because the JSON log format rejects duplicate keys.

## Frontend architecture

`frontend/src/`: `hooks/useChat.ts` owns session and message state. It creates the session lazily on first use and
drops late replies after "new chat". `services/api.ts` is the only module that calls `fetch`; it surfaces only the
problem title. Components are in `components/`. The browser holds no secrets: `VITE_API_BASE_URL` is optional and
non-secret, and Vite proxies `/api` to the backend, so the model provider is never called from the browser.

## Testing layout

- **Backend, offline by default:** `unit/` (no Spring context), `api/` (MockMvc, checked against the OpenAPI contract
  in `specs/001-support-chat-assistant/contracts/`), `integration/` (full context; `TestChatConfig` replaces the model
  with `FakeChatModel`; `LogLeakTest` fails if secrets or message text reach logs).
- **Backend, live:** `live/` runs only under `-Plive`. Tests tagged `live` are skipped without a key.
- **Frontend:** `tests/` with MSW handlers in `tests/mocks/`. `bundleSecrets.test.ts` runs a production build and
  scans `dist/` for secret values. When you add a secret variable, add it to that test.

## Non-obvious rules

- Secrets: `.env` is git-ignored. The Google key, and later the JWT signing key and email credentials, must never
  reach the React bundle, logs, or responses. Update `.env.example` with placeholders for every new variable.
- The v1 sample knowledge documents are placeholders. Do not present their content as Caramel AI facts.
- Specs and plans are checked against `.specify/memory/constitution.md`. Its Principle IV requires tests to be written
  before the behavior they cover.
