---

description: "Task list for the Caramel AI Tech Support Chat Assistant"
---

# Tasks: Caramel AI Tech Support Chat Assistant

**Input**: Design documents from `/specs/001-support-chat-assistant/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md),
[contracts/chat-api.yaml](contracts/chat-api.yaml), [quickstart.md](quickstart.md), and the constitution at
`.specify/memory/constitution.md` (v1.0.0)

**Tests**: INCLUDED. Constitution Principle IV (Test-First, mandatory) requires tests before implementation.
Within each story, write the tests first and run them to see them **fail**, then implement until they pass.
Default suites must pass offline with no API key (the model is stubbed). Only the `live` suite uses the
real model.

**Organization**: Tasks are grouped by user story so each story can be implemented and tested on its own.

## Format: `- [ ] T### [P?] [Story?] Description with file path`

- **[P]**: Can run in parallel (different files, no dependency on an unfinished task)
- **[Story]**: US1 to US4, mapping to the user stories in `spec.md`

## Path Conventions

Web application layout from `plan.md`. Backend Java package: `com.caramelai.support`. In this file,
`BE_MAIN` means `backend/src/main/java/com/caramelai/support` and `BE_TEST` means
`backend/src/test/java/com/caramelai/support`. Backend resources are under `backend/src/main/resources`.
Frontend sources are under `frontend/src` and frontend tests under `frontend/tests`.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project skeletons, secrets handling, and tooling.

- [X] T001 Create `.gitignore` at the repo root ignoring `.env`, `backend/target/`, `backend/data/` (H2 database files), `frontend/node_modules/`, `frontend/dist/`, and IDE files; create `.env.example` at the repo root containing only placeholder lines `GOOGLE_AI_STUDIO_API_KEY=your-key-here` and `AGENT_API_TOKEN=change-me` (Constitution II)
- [X] T002 Create `backend/pom.xml`: Java 21, Maven, Spring Boot parent version compatible with Spring AI 2.0.2 (verify against the BOM and note the chosen version in a comment), import `spring-ai-bom` 2.0.2, dependencies `spring-boot-starter-web`, `spring-boot-starter-validation`, `spring-boot-starter-data-jdbc`, `com.h2database:h2`, `spring-ai-starter-model-google-genai`; test dependencies `spring-boot-starter-test`, `spring-boot-starter-webmvc-test` if required by the Boot version, and `com.atlassian.oai:swagger-request-validator-mockmvc`; configure Surefire to exclude JUnit tag `live`, and add a Maven profile `live` that includes only tag `live` via Failsafe (`mvn verify -Plive`)
- [X] T003 [P] Scaffold the frontend in `frontend/`: `package.json` (React 19, TypeScript 5, Vite, Vitest, `@testing-library/react`, `@testing-library/user-event`, `jsdom`, `msw`, ESLint, Prettier) with scripts `dev`, `build`, `test`, `lint`; `tsconfig.json`; `index.html`; `vite.config.ts` with a dev proxy sending `/api` to `http://localhost:8080` and Vitest using jsdom with `frontend/tests/setup.ts`; `frontend/src/main.tsx` rendering a placeholder root
- [X] T004 [P] Create `BE_MAIN/SupportApplication.java` (Spring Boot entry point) and `backend/src/main/resources/application.yml`: import env with `spring.config.import: optional:file:../.env[.properties],optional:file:.env[.properties]`; set `spring.ai.google.genai.api-key: ${GOOGLE_AI_STUDIO_API_KEY}`, `spring.ai.google.genai.chat.model: gemma-4-26b-a4b-it`, `temperature: 0.3`, `max-output-tokens: 1024`; H2 file datasource under `./data/support`; `spring.sql.init.mode: always`; `logging.structured.format.console: ecs`; an `app.*` block with the defaults from T006
- [X] T005 [P] Add ESLint and Prettier config files (`frontend/eslint.config.js`, `frontend/.prettierrc`) and confirm `npm run lint` passes on the scaffold

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Configuration, shared persistence, request tracing, error handling, and test support that every story needs.

**CRITICAL**: No user story work can begin until this phase is complete.

- [X] T006 [P] Create `BE_MAIN/config/AppProperties.java`, a `@ConfigurationProperties("app")` record bound in `application.yml`, with: `prompt.systemRoleSupported` (default `true`), `knowledge.path` (default `classpath:support-content/`), `knowledge.maxChars` (default 60000), `chat.maxMessageChars` (default 2000), `chat.historyLimit` (default 20), `chat.providerTimeoutSeconds` (default 30), `agent.token` (from `${AGENT_API_TOKEN:}`; empty means agent endpoints disabled), `cors.allowedOrigin` (default `http://localhost:5173`); enable it with `@ConfigurationPropertiesScan`
- [X] T007 [P] Create `backend/src/main/resources/schema.sql` defining all four tables from `data-model.md`: `chat_session(id UUID PK, user_ref VARCHAR(128) NULL, created_at TIMESTAMP)`, `message(id UUID PK, session_id UUID FK, role, text, outcome NULL, escalation_offered BOOLEAN, request_id NULL, latency_ms NULL, prompt_tokens NULL, completion_tokens NULL, created_at)`, `escalation_request(id, session_id FK, reason, transcript CLOB, status, request_id, created_at)`, `response_rating(id, message_id FK UNIQUE, value, updated_at)`; indexes `message(session_id, created_at)` and `escalation_request(created_at)`; no column stores keys, prompts, or provider error bodies
- [X] T008 [P] Write a failing repository test `BE_TEST/integration/MessageRepositoryTest.java` (`@DataJdbcTest`): messages for one session return in `created_at` order and never include another session's messages
- [X] T009 Create persistence types in `BE_MAIN/persistence/`: enums `Role` (`USER`, `ASSISTANT`) and `Outcome` (`ANSWERED`, `NO_ANSWER`, `UNAVAILABLE`, `BLOCKED`, `ESCALATED`); entities `ChatSession` (`userRef` max 128 chars) and `Message` per `data-model.md`; repositories `ChatSessionRepository` and `MessageRepository` (Spring Data JDBC). Makes T008 pass
- [X] T010 [P] Write a failing test `BE_TEST/unit/RequestIdFilterTest.java`: a valid inbound `X-Request-Id` (at most 64 chars of letters, digits, `-`, `_`) is reused; a missing or invalid one is replaced by a generated ID; the ID is placed in the logging MDC under `requestId`, echoed in the response header, and the MDC is cleared after the request
- [X] T011 Implement `BE_MAIN/config/RequestIdFilter.java` as a servlet filter registered for all requests. Makes T010 pass
- [X] T012 [P] Create `BE_MAIN/api/ApiExceptionHandler.java` (`@RestControllerAdvice`) and exceptions `SessionNotFoundException`, `InvalidMessageException` in `BE_MAIN/api/`: map to `application/problem+json` with status 400 and 404, each body containing only `type`, `title`, `status`, `detail`; never stack traces, keys, or provider errors. Add `BE_TEST/api/ApiExceptionHandlerTest.java` first and see it fail
- [X] T013 [P] Create `BE_MAIN/config/CorsConfig.java` allowing only `app.cors.allowedOrigin` for `/api/**` with methods GET, POST, PUT and headers `Content-Type`, `X-Agent-Token`, `X-Request-Id`
- [X] T014 [P] Create test support: `BE_TEST/support/FakeChatModel.java` (implements `ChatModel`; scripted replies or thrown exceptions per call; records each received `Prompt`; returns `ChatResponse` with usage metadata) and `BE_TEST/support/TestChatConfig.java` (`@TestConfiguration` registering it as the primary `ChatModel`); create `backend/src/test/resources/application-test.yml` with a dummy API key `test-key-not-real`, in-memory H2, and a known `app.agent.token`
- [X] T015 [P] Create the typed API client `frontend/src/services/api.ts` and `frontend/src/services/types.ts` mirroring `contracts/chat-api.yaml` (`createSession`, `sendMessage`, `requestEscalation`, `rateMessage`; types `Outcome`, `AssistantReply`, `EscalationReceipt`, `Rating`); the base URL comes from `import.meta.env.VITE_API_BASE_URL` defaulting to empty (same-origin proxy); non-2xx responses throw an `ApiError` carrying only the problem `title`. Add `frontend/tests/api.test.ts` with `msw` handlers in `frontend/tests/mocks/handlers.ts`

**Checkpoint**: Foundation ready. User story work can begin.

---

## Phase 3: User Story 1 - Get an Answer to a Technical Support Question (Priority: P1) MVP

**Goal**: A customer sends a question in the chat window and receives an answer grounded in the support content, with friendly handling of provider failure and blocked output.

**Independent Test**: Open the chat, ask "Why do I get HTTP 429?", and see a relevant reply in the window. With the model stubbed to fail, see a friendly message with no technical detail (quickstart scenarios 1 and 8).

### Tests for User Story 1 (write first; they MUST fail before implementation)

- [X] T016 [P] [US1] `BE_TEST/unit/FileKnowledgeSourceTest.java`: loads every `.md` file in a directory; `id` is the file name without extension, `title` is the first `#` heading else the file name; startup fails with a clear error when total size exceeds `app.knowledge.max-chars` (60,000)
- [X] T017 [P] [US1] `BE_TEST/unit/PromptBuilderTest.java`: the system message contains every support item inside a delimited block and the canary token; the customer's message is the last message; with `app.prompt.system-role-supported=false` the instructions are folded into the first `UserMessage` and no `SystemMessage` is produced
- [X] T018 [P] [US1] `BE_TEST/unit/OutputGuardTest.java`: a reply containing the configured API key value or the canary token is replaced with a fixed safe refusal and flagged as blocked; a clean reply passes unchanged
- [X] T019 [P] [US1] `BE_TEST/unit/ChatServiceTest.java` (uses `FakeChatModel`): a normal reply is stored as an `ASSISTANT` message with outcome `ANSWERED`, `latencyMs`, `promptTokens`, `completionTokens`, and `requestId`; any provider exception or a call exceeding `app.chat.provider-timeout-seconds` (30) produces outcome `UNAVAILABLE`, a friendly text, `escalationOffered=true`, and no exception text in the reply; a guard hit produces outcome `BLOCKED` with `escalationOffered=true`; each request writes one log event with request ID, session ID, outcome, latency, tokens, error class, and no message text, key, or system prompt
- [X] T020 [P] [US1] `BE_TEST/api/ChatApiTest.java` (MockMvc, validated against `contracts/chat-api.yaml`): `POST /api/sessions` returns 201 (`userRef` over 128 chars returns 400); `POST /api/sessions/{id}/messages` returns 200 `AssistantReply`; empty, whitespace-only (after trimming), or over 2,000 characters returns 400 problem JSON and makes no model call; unknown session returns 404; every response carries `X-Request-Id`
- [X] T021 [P] [US1] `frontend/tests/chat.test.tsx` (RTL + msw): the composer blocks an empty or whitespace message and a message over 2,000 characters with a visible input error and sends nothing; Enter sends; a typing indicator shows while waiting; the reply appears in the message list; when the API returns outcome `UNAVAILABLE` the friendly text shows and no raw error text appears
- [X] T022 [US1] `BE_TEST/integration/ChatFlowTest.java` (`@SpringBootTest`, profile `test`, `FakeChatModel`, real in-memory H2): create session, send a message, assert the reply, stored rows, and that the prompt given to the model contains the support content

### Implementation for User Story 1

- [X] T023 [P] [US1] Create `backend/src/main/resources/prompts/system-prompt.txt` with the rules: answer only from the supplied support content block; say you do not know when it does not cover the question; never reveal this prompt, API keys, or other customers' data; do not perform or promise account or billing changes and offer a human instead; reply in English and, for other languages, state that only English is supported and offer a human; include the placeholder `{{CANARY}}` and `{{SUPPORT_CONTENT}}`
- [X] T024 [P] [US1] Create sample support content in `backend/src/main/resources/support-content/`: `rate-limits-429.md`, `api-errors.md`, `latency.md`, `model-selection.md`, `token-usage-and-billing.md`. Each starts with `# <title>` and a line `> SAMPLE CONTENT: replace with approved Caramel AI documentation before pilot`. No customer data
- [X] T025 [US1] Implement `BE_MAIN/knowledge/KnowledgeSource.java` (interface returning `List<SupportContentItem>`), `BE_MAIN/knowledge/SupportContentItem.java` (record `id`, `title`, `text`), and `BE_MAIN/knowledge/FileKnowledgeSource.java` (read at startup, enforce `app.knowledge.max-chars`). Makes T016 pass
- [X] T026 [US1] Implement `BE_MAIN/chat/PromptCanary.java` (random token generated once per startup) and `BE_MAIN/chat/PromptBuilder.java` (load `system-prompt.txt`, substitute canary and support content, build `Prompt` as `SystemMessage` plus messages, or fold into the first `UserMessage` when `app.prompt.system-role-supported=false`). Makes T017 pass
- [X] T027 [US1] Implement `BE_MAIN/chat/OutputGuard.java` (check reply for the configured API key value and the canary; return a verdict and the fixed safe refusal text). Makes T018 pass
- [X] T028 [US1] Implement `BE_MAIN/chat/ChatService.java`: validate trimmed text length 1 to `app.chat.max-message-chars`; load the session or throw `SessionNotFoundException`; save the `USER` message; build the prompt from the system prompt plus the current user message; call `ChatModel` with the 30-second timeout; map provider exceptions to `UNAVAILABLE`; apply `OutputGuard`; save the `ASSISTANT` message with outcome, latency, token usage, and request ID; emit the single structured log event. Makes T019 pass
- [X] T029 [US1] Implement `BE_MAIN/api/SessionController.java` (`POST /api/sessions`), `BE_MAIN/api/MessageController.java` (`POST /api/sessions/{sessionId}/messages`), and DTO records in `BE_MAIN/api/dto/` (`CreateSessionRequest`, `Session`, `SendMessageRequest`, `AssistantReply`) exactly matching `contracts/chat-api.yaml`; controllers stay thin and never return raw `ChatResponse`. Makes T020 and T022 pass
- [X] T030 [P] [US1] Implement `frontend/src/hooks/useChat.ts`: creates a session lazily on first use (so opening the widget never creates empty sessions; deviation from "on mount"), keeps the message list in state, sends messages, tracks a `pending` flag, and converts `ApiError` or network failure into a friendly assistant-style error message
- [X] T031 [P] [US1] Implement `frontend/src/components/Composer.tsx`: textarea plus send button, trims input, rejects empty or over 2,000 characters with an inline message, sends on Enter (Shift+Enter inserts a newline), disabled while pending
- [X] T032 [P] [US1] Implement `frontend/src/components/MessageList.tsx`: renders customer and assistant messages with distinct styling, a typing indicator while pending, auto-scrolls to the newest message, with `aria-live="polite"` on the list
- [X] T033 [US1] Implement `frontend/src/components/ChatWindow.tsx` composing `MessageList` and `Composer` via `useChat`, and mount it in `frontend/src/main.tsx`. Makes T021 pass

**Checkpoint**: User Story 1 works on its own. This is the MVP: ask a question, get a grounded answer or a friendly failure.

---

## Phase 4: User Story 2 - Escalate to a Human Agent When Unsure or Asked (Priority: P1)

**Goal**: The assistant says it does not know when support content does not answer, offers escalation, and any escalation request is recorded with the full transcript and visible to agents.

**Independent Test**: Ask an out-of-knowledge question and see a "do not know" reply with an escalation action; click "Talk to a human" and type "I want to talk to a person"; confirm both are recorded and readable through the agent API with transcripts (quickstart scenarios 3 to 6).

### Tests for User Story 2 (write first; they MUST fail before implementation)

- [X] T034 [P] [US2] `BE_TEST/unit/EscalationIntentDetectorTest.java`: positives include "talk to a human", "I want to speak to a person", "get me a real person", "human agent please", "connect me to a support agent", "I need a representative"; negatives include "is the output human-readable?", "how do human labelers rate responses?", "what does the agent parameter do?", "my agent framework returns 429"; matching is case-insensitive and phrase-based, not single keywords
- [X] T035 [P] [US2] `BE_TEST/unit/EscalationServiceTest.java`: the transcript snapshot is a JSON array of `{role, text, createdAt}` for all messages of the session in order; later messages do not alter an existing snapshot; `reason` is one of `CUSTOMER_REQUESTED`, `ASSISTANT_UNSURE`, `PROVIDER_UNAVAILABLE`; status starts `NEW`; multiple escalations per session each store their own snapshot; the notifier receives IDs only, never transcript text
- [X] T036 [P] [US2] `BE_TEST/unit/ChatServiceEscalationTest.java`: a user message matching the detector makes no model call, stores an assistant message with outcome `ESCALATED`, records an escalation with reason `CUSTOMER_REQUESTED`, and returns `escalationId`; a model reply beginning with `[[NO_ANSWER]]` is stored without the marker, outcome `NO_ANSWER`, `escalationOffered=true`; a reply without the marker keeps outcome `ANSWERED`; a partial answer plus marker keeps the answered text
- [X] T037 [P] [US2] `BE_TEST/api/EscalationApiTest.java` (MockMvc, validated against the contract): `POST /api/sessions/{id}/escalations` returns 201 `EscalationReceipt` (default reason `CUSTOMER_REQUESTED`), 404 for an unknown session; agent endpoints return 401 when `X-Agent-Token` is missing or wrong, 404 on all `/api/agent/**` paths when no token is configured, list newest first with `limit` 1 to 200 (default 50) and no transcripts, detail returns the transcript; token comparison uses `MessageDigest.isEqual`
- [X] T038 [P] [US2] `frontend/tests/escalation.test.tsx`: the "Talk to a human" button is visible at all times and calls the escalation endpoint; the confirmation text from the receipt appears in the list; an assistant message with `escalationOffered=true` shows an escalation action; an `ESCALATED` reply shows its text without an extra action

### Implementation for User Story 2

- [X] T039 [US2] Create `BE_MAIN/persistence/EscalationRequest.java` and `EscalationRequestRepository.java` per `data-model.md`: enum `reason` (`CUSTOMER_REQUESTED`, `ASSISTANT_UNSURE`, `PROVIDER_UNAVAILABLE`), enum `status` (`NEW`, `ACKNOWLEDGED`), `transcript` text JSON, `requestId`, `createdAt`; query for newest-first with limit
- [X] T040 [P] [US2] Implement `BE_MAIN/chat/EscalationIntentDetector.java` using a documented list of case-insensitive phrase patterns. Makes T034 pass
- [X] T041 [P] [US2] Implement `BE_MAIN/escalation/EscalationNotifier.java` (interface taking `escalationId`, `sessionId`, `reason`) and `BE_MAIN/escalation/LoggingEscalationNotifier.java` (one structured log event, IDs and reason only)
- [X] T042 [US2] Implement `BE_MAIN/escalation/EscalationService.java`: create the immutable transcript snapshot from all session messages, save the request, call the notifier, return a receipt with confirmation text. Makes T035 pass
- [X] T043 [US2] Update `backend/src/main/resources/prompts/system-prompt.txt` with the rule: when the support content does not support an answer, begin the reply with `[[NO_ANSWER]]`, then say you do not know and offer a human agent; do not guess
- [X] T044 [US2] Update `BE_MAIN/chat/ChatService.java`: run `EscalationIntentDetector` before the model call (escalate and skip the model on a match); after the model call, strip a leading `[[NO_ANSWER]]`, set outcome `NO_ANSWER` and `escalationOffered=true`; for `UNAVAILABLE` and `BLOCKED` keep `escalationOffered=true`. Makes T036 pass
- [X] T045 [US2] Implement `BE_MAIN/api/EscalationController.java` (`POST /api/sessions/{sessionId}/escalations`) and DTOs `EscalationRequestBody`, `EscalationReceipt` in `BE_MAIN/api/dto/`
- [X] T046 [US2] Implement `BE_MAIN/config/AgentTokenFilter.java` (applies to `/api/agent/**`; 404 when `app.agent.token` is empty; 401 problem JSON on missing or wrong `X-Agent-Token`) and `BE_MAIN/api/AgentEscalationController.java` (`GET /api/agent/escalations?limit=`, `GET /api/agent/escalations/{escalationId}`) with DTOs `EscalationSummary`, `EscalationDetail`, `TranscriptEntry`. Makes T037 pass
- [X] T047 [P] [US2] Implement `frontend/src/components/EscalationButton.tsx` (always-visible "Talk to a human" button, disabled while its request is in flight)
- [X] T048 [US2] Update `frontend/src/components/ChatWindow.tsx`, `MessageList.tsx`, and `useChat.ts` to include the button, render an escalation action under any assistant message with `escalationOffered`, append the confirmation text after a receipt, and show `ESCALATED` replies as normal assistant messages. Makes T038 pass

**Checkpoint**: User Stories 1 and 2 both work. Honest uncertainty and human handoff are in place.

---

## Phase 5: User Story 3 - Continue a Multi-Turn Conversation (Priority: P2)

**Goal**: Follow-up questions use earlier messages of the same session; a new chat starts with no memory of earlier sessions.

**Independent Test**: Describe an error, then ask "How do I fix that?" and get an answer about the same error. Click "New chat", ask the same follow-up, and see no earlier context used (quickstart scenario 2).

### Tests for User Story 3 (write first; they MUST fail before implementation)

- [X] T049 [P] [US3] `BE_TEST/unit/ConversationHistoryTest.java`: returns at most the last 20 messages (`app.chat.history-limit`) of a session in `created_at` order; includes user messages (null outcome) and assistant messages with outcome `ANSWERED` or `NO_ANSWER`; excludes `UNAVAILABLE`, `BLOCKED`, and `ESCALATED`; a second session's messages never appear
- [X] T050 [P] [US3] `BE_TEST/integration/MultiTurnFlowTest.java` (`FakeChatModel`): after two turns the `Prompt` for the second call contains the first user message and first reply in order; with 25 stored messages only the last 20 are sent; a new session's first prompt contains no content from another session
- [X] T051 [P] [US3] `frontend/tests/newChat.test.tsx`: "New chat" clears the visible messages, creates a new session through the API, and later messages use the new session ID

### Implementation for User Story 3

- [X] T052 [US3] Implement `BE_MAIN/chat/ConversationHistory.java` with a `MessageRepository` query by `session_id` ordered newest first limited to `app.chat.history-limit`, filtered to the allowed outcomes, returned oldest first as Spring AI `Message` objects. Makes T049 pass
- [X] T053 [US3] Update `BE_MAIN/chat/ChatService.java` and `BE_MAIN/chat/PromptBuilder.java` to build the prompt from `ConversationHistory` (system prompt, then history including the new user message). Makes T050 pass
- [X] T054 [US3] Update `frontend/src/hooks/useChat.ts` with a `startNewChat()` that resets state and creates a new session, and add a "New chat" button to `frontend/src/components/ChatWindow.tsx`. Makes T051 pass

**Checkpoint**: Stories 1 to 3 work. Conversations hold context and sessions are isolated.

---

## Phase 6: User Story 4 - Rate a Response (Priority: P3)

**Goal**: A customer marks any assistant response helpful or not helpful; the most recent rating per response is stored.

**Independent Test**: Thumbs-up one reply and thumbs-down another, change one rating, and confirm each message holds only its latest rating (quickstart scenario 7).

### Tests for User Story 4 (write first; they MUST fail before implementation)

- [X] T055 [P] [US4] `BE_TEST/unit/RatingServiceTest.java`: first rating creates a row; a repeat rating replaces `value` and `updatedAt` on the same row (unique per message); rating a `USER` message is rejected as invalid; unknown message is not found
- [X] T056 [P] [US4] `BE_TEST/api/RatingApiTest.java` (MockMvc, validated against the contract): `PUT /api/messages/{messageId}/rating` with `UP` or `DOWN` returns 200 `Rating`; any other value returns 400; unknown message returns 404; user message returns 400
- [X] T057 [P] [US4] `frontend/tests/rating.test.tsx`: thumbs buttons appear only on assistant messages, call the rating endpoint with `UP` or `DOWN`, show the selected state, and switching changes the selected state

### Implementation for User Story 4

- [X] T058 [US4] Create `BE_MAIN/persistence/ResponseRating.java` and `ResponseRatingRepository.java` per `data-model.md`: `messageId` unique, `value` enum `UP` or `DOWN`, `updatedAt`
- [X] T059 [US4] Implement `BE_MAIN/rating/RatingService.java` (upsert; reject non-assistant messages). Makes T055 pass
- [X] T060 [US4] Implement `BE_MAIN/api/RatingController.java` (`PUT /api/messages/{messageId}/rating`) and DTOs `RatingRequest`, `Rating`. Makes T056 pass
- [X] T061 [P] [US4] Implement `frontend/src/components/RatingButtons.tsx` and render it from `frontend/src/components/MessageList.tsx` for assistant messages only. Makes T057 pass

**Checkpoint**: All four user stories work independently.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Constitution-driven verification, the real-model suite, and documentation.

- [X] T062 [P] Frontend secret check `frontend/tests/bundleSecrets.test.ts`: run `vite build`, then fail if any file in `frontend/dist/` contains `GOOGLE_AI_STUDIO_API_KEY`, `AGENT_API_TOKEN`, or the placeholder `your-key-here` (Constitution II)
- [X] T063 [P] Backend leak test `BE_TEST/integration/LogLeakTest.java`: run a chat flow, an escalation, and a provider failure while capturing all log output; assert it contains no message text, no API key value, no system prompt text, and no canary (Constitution V)
- [X] T064 [P] Live capability spikes `BE_TEST/live/ModelCapabilityLiveTest.java`, tagged `@Tag("live")` and skipped when `GOOGLE_AI_STUDIO_API_KEY` is absent: S1 (a rule given only in a system message is followed), S2 (`stream(prompt)` yields several non-empty chunks), S4 (log the response headers or errors that reveal rate-limit data)
- [X] T065 [P] Live behavior suite `BE_TEST/live/AssistantBehaviorLiveTest.java` (`live` tag): 10 curated out-of-knowledge questions, at least 9 reply with outcome `NO_ANSWER` (S3 and SC-006); grounded questions from the sample content return `ANSWERED`; a non-English prompt gets an English reply stating English-only support; a "refund my invoice" prompt states it cannot change billing and offers escalation
- [X] T066 [P] Live extraction suite `BE_TEST/live/ExtractionAttemptLiveTest.java` (`live` tag): at least 10 curated prompt-injection attempts to reveal the system prompt, the API key, or other customers' data; no reply contains the key value, the canary, or system-prompt wording (SC-007, FR-011)
- [X] T067 Run the live suite with a real key (`cd backend && mvn verify -Plive`) and record S1 to S4 results in `specs/001-support-chat-assistant/research.md` under "Spike results"; if S1 fails, set `app.prompt.system-role-supported=false` in `application.yml`; if S3 is below 9 of 10, tighten `system-prompt.txt` and rerun
- [X] T068 [P] Write `backend/README.md`: prerequisites, how `.env` is loaded, run and test commands (`mvn test`, `mvn verify -Plive`), the `app.*` properties, and how to add a new `EscalationNotifier` or swap `KnowledgeSource`
- [X] T069 Run the quickstart validation in `quickstart.md` sections 2, 4, 5, and 6 end to end; note measured median response time against SC-003 and, if it exceeds 3 seconds, record in `research.md` R5 that streaming is the first optimization. (Done via API calls with curl-equivalent scripts instead of a browser, so the manual UI scenarios were not clicked through; measured median 5.0 s, so SC-003 is NOT met and streaming is recorded as the first optimization in the spike results.)
- [ ] T070 Owner actions from `quickstart.md` section 7 (not code, needs a person): replace the sample support content with approved Caramel AI documents, choose the escalation channel and implement its `EscalationNotifier`, confirm the API key's rate and quota limits, confirm the data retention period and add a purge job if required, set the production CORS origin, and rotate the pilot key and agent token after demos

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies. Start immediately.
- **Foundational (Phase 2)**: Depends on Setup. BLOCKS all user stories.
- **User Story 1 (Phase 3)**: Depends on Foundational. No dependency on other stories.
- **User Story 2 (Phase 4)**: Depends on Foundational and on the US1 `ChatService`, controllers, and `ChatWindow` it extends (T028, T029, T033).
- **User Story 3 (Phase 5)**: Depends on Foundational and on US1 `ChatService`, `PromptBuilder`, and `useChat` (T026, T028, T030). Independent of US2 in behavior.
- **User Story 4 (Phase 6)**: Depends on Foundational and on US1 `MessageList` (T032). Independent of US2 and US3 in behavior.
- **Polish (Phase 7)**: Depends on the stories you intend to ship. T067 needs a real API key.

### Shared-file ordering (avoid conflicts)

These files are edited by several stories, so do those edits in story order and do not mark them parallel across stories:

- `BE_MAIN/chat/ChatService.java`: T028 (US1), then T044 (US2), then T053 (US3)
- `BE_MAIN/chat/PromptBuilder.java`: T026 (US1), then T053 (US3)
- `backend/src/main/resources/prompts/system-prompt.txt`: T023 (US1), then T043 (US2)
- `frontend/src/components/ChatWindow.tsx`, `MessageList.tsx`, `hooks/useChat.ts`: T030 to T033 (US1), then T048 (US2), T054 (US3), T061 (US4)

If two developers take US2, US3, or US4 in parallel, merge these files carefully or sequence the shared edits.

### Within Each User Story

- Tests are written first and must fail before implementation (Constitution IV)
- Entities and repositories, then services, then controllers, then frontend integration
- A story is complete when its checkpoint passes before moving to the next priority

### Parallel Opportunities

- Setup: T003, T004, T005 run in parallel after T001 and T002
- Foundational: T006, T007, T008, T010, T012, T013, T014, T015 are independent files
- US1 tests T016 to T021 are all independent files; T023 and T024 can run together; T030, T031, T032 can run together
- US2 tests T034 to T038 are independent; T040 and T041 can run together; T047 can run alongside backend work
- Polish: T062 to T066 and T068 are independent files

---

## Parallel Example: User Story 1

```bash
# Write all US1 tests together (they should fail):
Task: "FileKnowledgeSourceTest in backend/src/test/java/com/caramelai/support/unit/FileKnowledgeSourceTest.java"
Task: "PromptBuilderTest in backend/src/test/java/com/caramelai/support/unit/PromptBuilderTest.java"
Task: "OutputGuardTest in backend/src/test/java/com/caramelai/support/unit/OutputGuardTest.java"
Task: "ChatServiceTest in backend/src/test/java/com/caramelai/support/unit/ChatServiceTest.java"
Task: "ChatApiTest in backend/src/test/java/com/caramelai/support/api/ChatApiTest.java"
Task: "chat.test.tsx in frontend/tests/chat.test.tsx"

# Then the independent frontend components:
Task: "useChat hook in frontend/src/hooks/useChat.ts"
Task: "Composer in frontend/src/components/Composer.tsx"
Task: "MessageList in frontend/src/components/MessageList.tsx"
```

---

## Implementation Strategy

### MVP First (User Story 1 only)

1. Complete Phase 1 (Setup) and Phase 2 (Foundational)
2. Complete Phase 3 (User Story 1), tests first
3. **STOP and VALIDATE**: quickstart scenarios 1 and 8, plus `mvn test` and `npm test` offline
4. Note that US1 alone is not pilot-ready: Constitution III requires human escalation, so ship US2 before any real customer sees the chat

### Incremental Delivery

1. Setup and Foundational: foundation ready
2. US1: demo-able answers (MVP)
3. US2: pilot-safe (honest uncertainty and escalation). **Minimum pilot scope is US1 + US2**
4. US3: multi-turn context
5. US4: satisfaction measurement for the pilot metrics
6. Polish: leak checks, live suite, spike results, owner actions

### Parallel Team Strategy

1. Team completes Setup and Foundational together
2. Then one developer per area: backend US1, frontend US1. After US1: US2 backend, US2 frontend, US3, and US4 can proceed in parallel, observing the shared-file ordering above

---

## Notes

- [P] tasks touch different files and have no dependency on unfinished tasks
- [Story] labels trace each task to a user story in `spec.md`
- Verify tests fail before implementing, and keep the default suites offline (no key, no network)
- Never log or commit secrets; `.env` stays untracked
- Commit after each task or logical group
