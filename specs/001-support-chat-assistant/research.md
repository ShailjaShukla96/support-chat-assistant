# Phase 0 Research: Caramel AI Tech Support Chat Assistant

**Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)

Written for: engineers building v1. Each entry gives the decision, why, and what was rejected.
Entries marked **VERIFY** depend on facts that could not be confirmed offline. They become spike
tasks with a defined fallback, so the design never relies on an unverified capability.

## R1. Backend and Spring AI versions

- **Decision**: Java 21, Maven, Spring AI **2.0.2** (pinned through `spring-ai-bom`), with the Spring
  Boot version that the 2.0.x BOM line targets. Provider starter:
  `spring-ai-starter-model-google-genai`.
- **Rationale**: The constitution fixes Spring Boot + Spring AI. Java 21 is installed locally and
  provides virtual threads. The `spring-ai-chatbot` project skill documents 2.0.2 and warns that the old
  `spring-ai-vertex-ai-gemini` module was removed.
- **VERIFY (scaffold task)**: Confirm the exact Spring Boot minor version compatible with Spring AI
  2.0.2 when generating `pom.xml`. If a signature in this plan does not compile, trust the pinned
  reference docs, not this plan.
- **Alternatives rejected**: Spring AI 1.x (older, different property names); Gradle (no stated need,
  Maven is installed and simpler to script in the quickstart).

## R2. Model access: `gemma-4-26b-a4b-it` on Google AI Studio

- **Decision**: Configure through `spring.ai.google.genai.api-key` (selects the Developer API instead of
  Vertex AI) and `spring.ai.google.genai.chat.model=gemma-4-26b-a4b-it`, low temperature (0.3),
  `max-output-tokens` 1024. Business code depends only on `ChatModel`; provider specifics stay in one
  config class.
- **Rationale**: Keeps the provider swappable (spec assumption: no dependency on a specific provider) and
  matches the constitution's fixed stack.
- **Alternatives rejected**: Calling the Gemini REST API directly (loses Spring AI, violates the fixed
  stack); `ChatClient` (adds a layer; `ChatModel` is enough for one prompt builder).

## R3. Does the model accept system instructions? (PRD open question 4, part 1)

- **VERIFY (spike S1)**: The skill notes Gemma support for system instructions is unconfirmed.
- **Decision**: `PromptBuilder` builds a `SystemMessage` by default, behind the property
  `app.prompt.system-role-supported` (default `true`). If the spike shows system messages are rejected or
  ignored, set it to `false` and the builder folds the instructions into the first `UserMessage` instead.
  Unit tests cover both modes, so the fallback needs no redesign.
- **Rationale**: Grounding and secrecy rules (Constitution I, II) live in these instructions, so the
  behavior must be tested in the live suite against the real model either way.

## R4. Tool/function calling (PRD open question 4, part 2)

- **Decision**: **Do not use function calling in v1.** Escalation is handled by deterministic
  application logic instead of model-chosen tools:
  1. A permanently visible "Talk to a human" button in the UI calls the escalation endpoint.
  2. A phrase-based `EscalationIntentDetector` on the user's message catches free-text requests such as
     "let me talk to a person" and escalates without asking the model.
  3. The system prompt instructs the model to begin any reply it cannot ground in the provided content with
     the marker `[[NO_ANSWER]]`. The backend strips the marker, sets `outcome=NO_ANSWER`, and sets
     `escalationOffered=true` so the UI shows an escalate action.
- **Rationale**: The PRD asks us to verify tool-calling support before committing. Avoiding the dependency
  removes the risk, makes escalation testable without a model (Constitution III, IV), and satisfies "any
  time" (FR-008) even if the model misbehaves.
- **Trade-off**: The marker can be omitted by the model, so an unmarked "I don't know" would not raise the
  button flag. The always-visible button covers that case, and the live suite measures marker compliance
  (SC-006).
- **Alternatives rejected**: Spring AI tool calling (unverified for this model); asking the model to
  classify intent (non-deterministic, adds a second call and latency).

## R5. Streaming vs complete responses (PRD open question 4, part 3)

- **VERIFY (spike S2)**: Streaming through the GenAI client with this model is unconfirmed.
- **Decision**: v1 ships **complete responses** (`POST` returns JSON). FR-002 allows either. If spike S2
  passes, an SSE endpoint may be added as a follow-up feature with its own contract, since `ChatModel`
  already extends `StreamingChatModel`. The UI shows a typing indicator while waiting.
- **Risk**: A complete response from a 26B-parameter model may approach the 3-second median target (SC-003).
  Mitigation: `max-output-tokens` 1024, a 20-message history cap, and prompt size limits (R6). Measure
  actual latency in the pilot logs. If it misses, streaming becomes the first optimization.
- **Alternatives rejected**: Building streaming first (unverified, extra frontend complexity and error
  handling for a capability the spec does not require).

## R6. Knowledge source for v1 (PRD open question 1)

- **Decision**: Load support documents into the prompt. Content is Markdown files under
  `backend/src/main/resources/support-content/`, read at startup by a `KnowledgeSource` interface with a
  `FileKnowledgeSource` implementation. All documents are placed in the system prompt inside a delimited
  block. A startup check fails fast if the total exceeds `app.knowledge.max-chars` (default 60,000 characters,
  roughly 15k tokens).
- **Rationale**: The PRD permits "loaded into the prompt or a simple local vector store". Prompt loading
  needs no embedding model, so there is one provider dependency, not two. Constitution V (simplicity)
  and "no external vector service" are met. The interface keeps a later swap to a local vector store
  (for example Spring AI `SimpleVectorStore`) a one-class change.
- **Content caveat**: Real Caramel AI documents are not in the repo. v1 ships **clearly labelled sample
  content** for tests and demos. Real content selection (including whether past tickets are used) stays an
  owner decision tracked in the spec assumptions. Past tickets are excluded from v1 because they may contain
  other customers' data (Constitution II).
- **Alternatives rejected**: Vector store now (needs an embedding model and chunking for a corpus that
  fits in the prompt); keyword search (adds retrieval logic with no clear gain at this size).
- **Revisit trigger**: corpus larger than the cap, or measurable quality loss from long prompts.

## R7. Escalation channel and agent access (PRD open question 2)

- **Decision**: v1 **records** escalations in the database with a transcript snapshot and exposes them to
  agents through a read-only, token-protected API (`GET /api/agent/escalations`). An `EscalationNotifier`
  interface has one v1 implementation that logs a structured event (no transcript text in the log). Email,
  ticketing, or live handoff plug in as new implementations later.
- **Rationale**: Spec FR-009 requires recording and agent visibility, not a delivery channel. A recorded,
  queryable escalation is the common denominator for every channel the owners might pick.
- **Agent auth**: shared secret in `AGENT_API_TOKEN` (from `.env`), checked in a filter on `/api/agent/**`
  with constant-time comparison. Endpoints are disabled (404) if the token is unset. This is a
  deliberate minimum, not full agent authentication, which is out of scope per the spec.
- **Alternatives rejected**: Building an agent UI (out of v1 scope); leaving agent endpoints open
  (would expose transcripts, violating Constitution II).

## R8. Persistence

- **Decision**: Embedded **H2 in file mode** with Spring Data JDBC and a `schema.sql`, giving durable
  storage of sessions, messages, escalations, and ratings with no external service. Conversation history
  for the prompt is read from the database for the session, so the service holds no in-memory session map
  and survives restarts.
- **Rationale**: Escalation records, ratings, and token/latency numbers must survive restarts to be useful
  in a pilot. A file database is the smallest durable option. Repositories sit behind Spring Data interfaces,
  so moving to PostgreSQL is a configuration and dialect change.
- **Retention**: The spec leaves the period to Caramel AI's data policy. v1 keeps all records and has no
  purge job; the quickstart lists "confirm the retention period and add a purge job if required" as an
  owner action before pilot. No data is used for training, since nothing sends stored data to any
  training pipeline.
- **Alternatives rejected**: In-memory only (loses escalations on restart); PostgreSQL now (extra service
  to run for a single-instance pilot); JPA (heavier than needed for five simple tables).

## R9. Conversation memory handling

- **Decision**: Per request, load the session's messages with outcome `ANSWERED` or `NO_ANSWER` plus user
  messages, keep the **last 20 messages**, and prepend the system prompt. Provider-error messages are shown
  to the customer and stored but never replayed to the model. Sessions are isolated by `sessionId`
  (a random UUID); no query ever reads across sessions (FR-004).
- **Rationale**: The model is stateless, so history must be resent. A fixed cap bounds tokens and
  latency. Spring AI chat memory advisors are not needed for a single prompt builder.
- **Alternatives rejected**: Summarizing old turns (extra model call, risk of drift).

## R10. Input and output guardrails

- **Decision**:
  - Input: reject empty or whitespace-only text; reject text over **2,000 characters** with a 400
    problem response (resolves the spec's deferred "length limit").
  - Output `OutputGuard`: before returning a reply, check it does not contain the configured API key value
    or the prompt **canary string** embedded in the system prompt. On a hit, replace the reply with a safe
    refusal and log a security event (without the leaked text).
  - Non-English input: the system prompt instructs an English reply that states English-only support and
    offers escalation. Verified in the live suite.
- **Rationale**: Cheap, deterministic checks that enforce Constitution II even when the model is tricked.
  They are not a substitute for prompt design, which the live adversarial suite exercises.
- **Alternatives rejected**: A second model call as a safety classifier (latency, cost); regex language
  detection library (the model handles this reliably enough, and the live suite measures it).

## R11. Provider failure handling

- **Decision**: Wrap the model call with a 30-second timeout. Any provider exception (rate limit,
  quota, timeout, safety block, server error) maps to a single outcome `UNAVAILABLE`: HTTP 200 with a
  friendly message, `escalationOffered=true`, and the error class logged with the request ID but never the
  prompt or key. No automatic retries in v1, because a retry doubles worst-case latency against a quota
  the team does not yet know (PRD open question 3).
- **Quota planning (PRD open question 3)**: Unknown. The plan requires only graceful failure plus metrics.
  The pilot checklist in the quickstart tells the owners to confirm the free-tier or paid limits for the
  key before pilot, using the request/token logs to size load.
- **Alternatives rejected**: Resilience4j retry/circuit breaker (extra dependency before limits are known).

## R12. Observability

- **Decision**: Servlet filter assigns a request ID (accepts a valid inbound `X-Request-Id`, else generates
  one), places it in the logging context, and echoes it as a response header. Console logging uses Spring
  Boot's built-in structured JSON format. Each chat request logs one event: request ID, session ID, outcome,
  latency ms, prompt/completion tokens (from response usage metadata), and error class. Latency and tokens
  are also stored on the assistant message for SC measurement queries. Log content never includes message
  text, the key, or the system prompt.
- **Alternatives rejected**: Micrometer + external metrics backend (no stated need for v1).

## R13. Frontend

- **Decision**: React + TypeScript on Vite; Vitest + React Testing Library + `msw` to stub the API in
  tests. State is local React state with a small `useChat` hook; no state library. The widget is a
  standalone page/component the owners can mount in their site. API base URL comes from `VITE_API_BASE_URL`
  (non-secret). Vite dev server proxies `/api` to the backend; production CORS allows only the configured
  origin.
- **Secrets**: Nothing secret exists in the frontend. A test greps the production bundle for the API key
  environment variable name and for the placeholder key string (Constitution II).
- **Alternatives rejected**: Redux/Zustand (one hook is enough); Next.js (no SSR need).

## R14. Testing strategy (Constitution IV)

- **Decision**:
  - **Default suite** (`mvn test`, `npm test`): no network, no key. `ChatModel` is a Mockito stub or a
    `FakeChatModel` test bean. Covers prompt building, history cap, session isolation, escalation intent,
    marker handling, output guard, error mapping, validation, agent token filter, rating upsert, and
    controller contracts through MockMvc.
  - **Contract tests**: MockMvc responses are validated against `contracts/chat-api.yaml`.
  - **Live suite** (`mvn verify -Plive`, JUnit tag `live`, excluded by default): calls the real model with
    a curated question set: grounded answers, out-of-knowledge questions (SC-006), key and prompt
    extraction attempts (SC-007), non-English input, billing-change requests, marker compliance, and the
    spikes S1 and S2. Requires `GOOGLE_AI_STUDIO_API_KEY`.
  - **Frontend**: component tests for message list, composer validation, error state with escalation
    action, escalation button, rating buttons, and the bundle secret check.
- **Order**: tests are written first for each task (red, green, refactor), as the constitution requires.

## R15. Spikes

| ID | Question | Pass condition | Fallback if it fails |
|----|----------|----------------|----------------------|
| S1 | Does the model honor a system message? | Reply follows a rule set only in the system message | `app.prompt.system-role-supported=false` |
| S2 | Does streaming work through Spring AI for this model? | `stream(prompt)` yields multiple non-empty chunks | Stay on complete responses |
| S3 | Does the model follow the `[[NO_ANSWER]]` marker rule? | At least 9 of 10 curated out-of-knowledge questions start with the marker | Rely on the always-visible escalation button and tighten the prompt |
| S4 | What are the key's rate and quota limits? | Limits recorded from the AI Studio console | Plan load around the lowest published limit |

All spikes run in the live suite once a key is available. Results are recorded in this file under
"Spike results" by the implementer.

## Spike results

Run on 2026-11-14 with `cd backend && mvn verify -Plive` against `gemma-4-26b-a4b-it` through Google AI Studio
(Spring AI 2.0.2, Spring Boot 4.0.9). Final run: 8 of 8 live tests pass.

| ID | Result | Detail |
|----|--------|--------|
| S1 System-role support | **PASS** | A rule given only in a `SystemMessage` was followed. Keep `app.prompt.system-role-supported=true`. |
| S2 Streaming | **PASS** | `stream(prompt)` yields more than one non-empty chunk. Streaming works, so it is available as the latency optimization below. v1 still ships complete responses. |
| S3 Marker compliance | **PASS** (at least 9 of 10) | Out-of-knowledge questions come back as `NO_ANSWER`. SC-006 met in this sample. |
| S4 Quota limits | **NOT DISCOVERABLE FROM CODE** | The API does not expose quota ceilings. The owner must read them in the AI Studio console (owner task T070). The run made roughly 60 requests in a few minutes without a rate-limit error, but one probe request failed transiently with a provider exception, which the app handled as `UNAVAILABLE`. |

Behavior checks (all pass on the final run): grounded 429 question answered; Spanish input gets an English reply
stating English-only support; a refund and plan-downgrade request is declined with a human offer; 10 prompt-injection
attempts revealed no key, canary, or system-prompt wording (SC-007).

### Findings from the live run (bugs the offline suite could not catch)

1. **The model returns separate "thought" generations.** A response has two or more `Generation`s; reasoning parts are
   flagged `isThought=true` in the message metadata and usually come first, and are often empty text. The first live
   run made 12 of 14 replies `UNAVAILABLE` ("empty reply") because the code read `getResult()`, which is the first
   generation. One reply instead **exposed the model's reasoning, including wording of the system-prompt rules**.
   Fixed in `ChatService.extractText`: thought generations are skipped and answer generations are joined. Offline
   regression tests use `FakeChatModel.thenReplyWithThought(...)`. `includeThoughts=false` did not change this
   behavior for this model.
2. **`thinkingBudget=0` is rejected** by this model (provider error). Do not set it.
3. **Thinking tokens are reported separately** (`thoughtsTokenCount`), so `completionTokens` understates the work done.

### Measured latency (complete responses)

About 15 timed calls with the real prompt (about 1,200 prompt tokens, `max-output-tokens` 1024): typically **2.3 to 8
seconds**, median roughly **3.7 seconds**, with one outlier near 18 seconds. This is **above the SC-003 target
(median under 3 seconds)** and above SC-004 (95% under 5 seconds). Decision: streaming is the first optimization
(S2 passed, so it is feasible), plus a smaller or non-thinking model setting if the provider offers one. This is a
follow-up feature; it needs its own contract and is not part of v1 as specified. Treat SC-003 and SC-004 as **not yet met**.

End-to-end through the running app (quickstart run, 5 model-backed requests): 3.1, 3.4, 5.0, 9.0, and 14.5 seconds,
median **5.0 seconds**. Messages that never call the model (free-text human request) take about 30 milliseconds.
