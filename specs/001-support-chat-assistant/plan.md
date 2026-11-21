# Implementation Plan: Caramel AI Tech Support Chat Assistant

**Branch**: `001-support-chat-assistant` | **Date**: 2026-11-14 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/001-support-chat-assistant/spec.md`

## Summary

Build a web chat assistant that answers Caramel AI customers' LLM-inferencing support questions from
supplied support content, keeps per-session context, admits when it does not know, and hands off to a human
agent at any time. A React + TypeScript widget talks over HTTP to a Spring Boot service. The service builds
prompts, calls `gemma-4-26b-a4b-it` through Spring AI's Google AI Studio client, and persists sessions,
messages, escalations (with transcript snapshots), and ratings in an embedded file database.

Approach, from [research.md](research.md):

- Support documents are loaded into the system prompt (no vector store, no embedding model) behind a
  `KnowledgeSource` interface (R6).
- Escalation is deterministic: an always-visible button, a phrase detector for free text, and a
  `[[NO_ANSWER]]` marker that the backend strips to raise an offer flag. **No tool calling** is used because
  model support is unverified (R4).
- v1 returns complete responses. Streaming and system-role support are verified in spikes S1 and S2, each
  with a defined fallback (R3, R5).
- Guardrails are deterministic: input length limit, an output guard against key and canary leakage, and
  one friendly outcome for every provider failure (R10, R11).

## Technical Context

**Language/Version**: Java 21 (backend); TypeScript 5.x on Node 22 (frontend)

**Primary Dependencies**: Spring Boot with Spring AI 2.0.2 (`spring-ai-starter-model-google-genai`,
version pinned via `spring-ai-bom`), Spring Web, Spring Data JDBC, H2; React 19 with Vite

**Storage**: Embedded H2, file mode, Spring Data JDBC with `schema.sql` (sessions, messages, escalations,
ratings). Support content as Markdown files read at startup.

**Testing**: JUnit 5, Mockito, Spring Boot Test, MockMvc for the backend, with the model stubbed. A
`live`-tagged suite for the real model (excluded by default). Vitest, React Testing Library, and `msw` for
the frontend.

**Target Platform**: Linux server (single instance) for the backend; modern evergreen browsers for the UI

**Project Type**: Web application (backend service + frontend widget)

**Performance Goals**: Median complete response under 3 seconds under pilot load (SC-003). First visible
response within 5 seconds for 95% of messages (SC-004). With complete responses, SC-004 equals total response
time, which is the main latency risk (research R5).

**Constraints**: API key server-side only and never in the bundle, logs, or responses. Message text at most
2,000 characters. Prompt history capped at 20 messages. Support content capped at 60,000 characters. Provider
call timeout 30 seconds. English only.

**Scale/Scope**: Pilot, single backend instance, low hundreds of concurrent sessions at most. Three main
screens' worth of UI: message list, composer with escalation button, and rating controls. Five tables.

**Open items carried as spikes** (not blocking design): S1 system-role support, S2 streaming support, S3
marker compliance, S4 key quota limits. See [research.md](research.md) R15.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Checked against `.specify/memory/constitution.md` v1.0.0.

| Principle / rule | Pre-research | Post-design | How the design satisfies it |
|------------------|:-----------:|:-----------:|-----------------------------|
| I. Grounded answers, honest uncertainty | PASS | PASS | Only support content goes in the prompt; system prompt demands "do not know" plus the `[[NO_ANSWER]]` marker; live suite measures SC-006 (R4, R6, R15 S3). |
| II. Secrets server-side (NON-NEGOTIABLE) | PASS | PASS | Key read from `.env` into Spring config only; `.env` gitignored with `.env.example`; bundle grep test; output guard blocks key and canary leaks; past tickets excluded from content; agent endpoints token-protected (R7, R10, R13). |
| III. Human escalation always available | PASS | PASS | Always-visible button, phrase detector, offer flag on `NO_ANSWER`/`UNAVAILABLE`; every request stored with an immutable transcript snapshot (R4, R7; data model). |
| IV. Test-first quality | PASS | PASS | Default suites are offline with a stubbed model; live suite is separate and explicitly invoked; tasks must write tests before code (R14). |
| V. Observability without leakage | PASS | PASS | Request ID filter, structured JSON logs with outcome, latency, tokens, error class; no message text, key, or prompt in logs; data not used for training (R12). |
| Stack fixed (Spring Boot, Spring AI, React + TS, model, AI Studio) | PASS | PASS | No substitutions. H2 and Vite are supporting choices, not stack changes. |
| UI never calls the provider directly | PASS | PASS | The UI calls only `/api/*` on the backend. |
| Session-only memory | PASS | PASS | History is read by `sessionId` only; no cross-session query exists (data model). |
| Knowledge source: prompt or simple local store; no external vector service | PASS | PASS | Prompt loading with a size cap (R6). |
| v1 out-of-scope items not built | PASS | PASS | No data-modifying account or billing actions, voice, personalization, or other languages. `userRef` is stored but unused for answers. |
| Simplicity (YAGNI), additions justified | PASS | PASS | No vector store, retries, circuit breaker, metrics backend, or state library. Additions are justified in Complexity Tracking. |
| Open Decisions resolved or tracked before dependent work | PASS | PASS | Knowledge sources (R6), escalation channel (R7), quotas (R11, S4), and model capabilities (R3-R5) are each decided or turned into a spike with a fallback. |

**Result**: No violations. No gate failures; planning proceeds.

## Project Structure

### Documentation (this feature)

```text
specs/001-support-chat-assistant/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/
│   └── chat-api.yaml    # Phase 1 output (/speckit-plan command)
├── checklists/
│   └── requirements.md  # Spec quality checklist (/speckit-specify command)
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
.env.example                         # placeholders only; .env is gitignored
.gitignore

backend/
├── pom.xml
├── src/
│   ├── main/
│   │   ├── java/com/caramelai/support/
│   │   │   ├── SupportApplication.java
│   │   │   ├── config/              # AI config, properties, CORS, request-ID filter, agent-token filter
│   │   │   ├── api/                 # controllers, DTOs, problem handler
│   │   │   ├── chat/                # ChatService, PromptBuilder, EscalationIntentDetector, OutputGuard
│   │   │   ├── knowledge/           # KnowledgeSource, FileKnowledgeSource
│   │   │   ├── escalation/          # EscalationService, EscalationNotifier, LoggingEscalationNotifier
│   │   │   ├── rating/              # RatingService
│   │   │   └── persistence/         # entities and Spring Data JDBC repositories
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── schema.sql
│   │       ├── prompts/system-prompt.txt
│   │       └── support-content/     # sample Markdown content (replace before pilot)
│   └── test/
│       ├── java/com/caramelai/support/
│       │   ├── unit/                # prompt, intent, guard, history, knowledge
│       │   ├── api/                 # MockMvc tests, validated against contracts/chat-api.yaml
│       │   ├── integration/         # full flow with FakeChatModel and real H2
│       │   └── live/                # @Tag("live") real-model suite and spikes
│       └── resources/
└── README.md

frontend/
├── package.json
├── vite.config.ts                   # dev proxy for /api
├── src/
│   ├── components/                  # ChatWindow, MessageList, Composer, EscalationButton, RatingButtons
│   ├── hooks/                       # useChat
│   ├── services/                    # typed API client
│   └── main.tsx
└── tests/                           # Vitest + RTL + msw, including the bundle secret check
```

**Structure Decision**: Web application layout with separate `backend/` and `frontend/` projects. This
matches the constitution's fixed two-tier stack and keeps the secret boundary physical: only `backend/`
reads `.env` values. Backend packages are split by responsibility so the model call, prompt building, and
guardrails can each be tested without the web layer.

## Complexity Tracking

> Fill ONLY if Constitution Check has violations that must be justified

No constitution violations. The additions below go beyond the minimum and are listed so reviewers can
challenge them:

| Addition | Why Needed | Simpler Alternative Rejected Because |
|----------|------------|--------------------------------------|
| H2 file database | Escalations, ratings, and token/latency data must survive restarts to be useful in a pilot (FR-009, FR-013, FR-014) | In-memory storage loses escalation records on restart |
| `OutputGuard` (key and canary check) | Deterministic enforcement of Constitution II when the model is manipulated (FR-011) | Prompt wording alone cannot be tested to a pass/fail standard |
| `EscalationNotifier` interface with a logging implementation | The delivery channel is undecided; the seam lets owners add email or ticketing without touching callers | Hardcoding a channel would guess at an open decision |
| `KnowledgeSource` interface | Allows a later swap to a local vector store when content outgrows the prompt | Reading files inline in the prompt builder blocks that swap and hurts testing |
| Agent token filter | Agents must view transcripts (FR-009) and open endpoints would expose customer data (Constitution II) | Leaving agent endpoints open violates Principle II; full agent login is out of v1 scope |
