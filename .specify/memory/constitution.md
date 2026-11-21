<!--
SYNC IMPACT REPORT (temporary; remove before committing)
Version change: (unfilled template) → 1.0.0
Modified principles: none renamed (initial ratification; all template placeholders filled)
Added principles:
  - I. Grounded Answers, Honest Uncertainty
  - II. Secrets Stay Server-Side (NON-NEGOTIABLE)
  - III. Human Escalation Always Available
  - IV. Test-First Quality
  - V. Observability Without Leakage
Added sections: Technology & Scope Constraints; Development Workflow & Quality Gates
Removed sections: none
Deferred items / follow-up TODOs:
  - PRD §8 open questions (knowledge sources, escalation channel, API key quota limits,
    gemma-4-26b-a4b-it support for function calling/streaming via Spring AI) are NOT resolved
    here. Each is recorded under "Open Decisions" and must be settled in a spec/plan.
  - Principle IV (Test-First) and the merge gate requiring passing tests are not stated in the
    PRD; they were added by this constitution. Confirm the team accepts them.
Source: PRD.md (Caramel AI Tech Support Chat Assistant)
-->

# Caramel AI Tech Support Chat Assistant Constitution

## Core Principles

### I. Grounded Answers, Honest Uncertainty
The assistant MUST answer only from Caramel AI support content (FAQs, docs, troubleshooting
guides) supplied to it through the prompt or the retrieval store. When the supplied content does
not support an answer, the assistant MUST say it does not know and offer escalation; it MUST NOT
guess or invent API behavior, limits, prices, or error codes. The system prompt MUST encode this
rule and tests MUST cover an out-of-knowledge question.

Rationale: customers act on technical guidance; a confident wrong answer about rate limits or
billing costs more than an escalation (PRD FR-3, FR-4).

### II. Secrets Stay Server-Side (NON-NEGOTIABLE)
The Google AI Studio API key MUST be read from `.env` on the backend and mapped into Spring
configuration. It MUST NOT appear in the React bundle, in browser-reachable responses, in logs, or
in version control. `.env` MUST be listed in `.gitignore` and a `.env.example` containing only
placeholder values MUST be maintained. Assistant responses MUST NOT expose API keys, the internal
system prompt, or any other customer's data, and the system prompt and session handling MUST be
designed and tested to enforce this.

Rationale: a leaked key or cross-customer data exposure is an irreversible incident (PRD FR-6,
NFR Security).

### III. Human Escalation Always Available
A customer MUST be able to request a human agent at any point in a conversation, and the
assistant MUST also offer escalation when it is unsure. Every escalation MUST be logged together
with the session transcript. If the model provider is unavailable, the UI MUST show a friendly
error message and offer escalation rather than a raw failure.

Rationale: the product goal is to resolve common issues quickly and hand off the rest; a dead end
is the worst customer outcome (PRD §1, FR-5, NFR Availability).

### IV. Test-First Quality
Behavior changes MUST be driven by tests written before the implementation (red → green →
refactor). Backend logic (session handling, prompt construction, escalation, error handling) MUST
have automated tests with the LLM client mocked or stubbed so the suite is deterministic and does
not call the real provider or require the real API key. Frontend components that render chat,
streaming/complete responses, errors, and the escalation action MUST have automated tests.
Anything that touches the real model MUST live in a separate, explicitly invoked suite.

Rationale: LLM output is non-deterministic; the surrounding application logic is the part that can
and must be verified reliably.

### V. Observability Without Leakage
For every chat request the backend MUST log a request ID, latency, token usage, and any error, in
structured form. Logs MUST NOT contain secrets, the API key, or the system prompt. Conversation
logs MUST be retained per Caramel AI's data policy and MUST NOT be used to train models.

Rationale: the pilot is judged on resolution rate, satisfaction, and median response time, which
cannot be measured without telemetry, and telemetry must not become a leak path (PRD NFR
Observability, Privacy, §7).

## Technology & Scope Constraints

- **Stack (fixed for v1):** Java with Spring Boot and Spring AI for the backend; React with
  TypeScript for the frontend; LLM `gemma-4-26b-a4b-it` served through Google AI Studio (Gemini
  API). Changing any of these requires a constitution amendment.
- **Interaction:** the React UI MUST talk to the Spring Boot service over HTTP; it MUST NOT call
  the model provider directly. Streaming is optional; complete responses are acceptable.
- **Session memory:** conversation context MUST be kept for the duration of a session only.
- **Knowledge source (v1):** support documents loaded into the prompt or a simple local vector
  store, retrieved per query. No external vector service without an amendment.
- **Out of scope for v1 (MUST NOT be built):** account changes or billing actions that modify
  data; voice support; authentication-based personalization beyond passing a session/user ID;
  languages other than English.
- **Performance targets:** first response token within a few seconds under normal load; median
  response time under 3 seconds.
- **Pilot success targets:** at least 60% of conversations resolved without escalation and at
  least 70% thumbs-up satisfaction. Features SHOULD be prioritized by their effect on these.

### Open Decisions (unresolved in the PRD)
These MUST be resolved in the relevant spec or plan before dependent work begins:

- Which knowledge sources (docs, FAQs, past tickets) feed v1.
- The escalation channel (email, ticketing system, live handoff).
- Rate limits and quota ceilings on the Google AI Studio key.
- Whether `gemma-4-26b-a4b-it` supports the function calling and streaming features planned
  through Spring AI; this MUST be verified before the design relies on either.

## Development Workflow & Quality Gates

- Work follows the Spec Kit flow: specify → plan → tasks → implement. Specs and plans MUST be
  checked against this constitution; violations MUST be justified in the plan's complexity
  tracking or the design changed.
- A change MUST NOT be merged unless the automated backend and frontend test suites pass.
- Reviewers MUST verify: no secrets in the diff or logs, no out-of-scope features, escalation path
  intact, and new behavior covered by tests.
- Prefer the simplest design that meets the PRD (YAGNI); added layers, services, or dependencies
  MUST be justified in the plan.
- Runtime development guidance lives in `CLAUDE.md`; it MUST NOT contradict this constitution.

## Governance

This constitution supersedes other project practices. Amendments MUST be proposed in writing,
state the reason and affected principles, and be approved by the project owner(s). An amendment
that changes the fixed stack, removes or redefines a principle, or lifts a v1 out-of-scope item
MUST include a migration plan for existing specs and code.

Versioning follows semantic versioning: MAJOR for backward-incompatible removals or redefinitions
of principles or governance; MINOR for a new principle/section or materially expanded guidance;
PATCH for clarifications and wording fixes. The Open Decisions list is updated as items are
resolved and such updates are PATCH changes unless they alter a principle.

Compliance is reviewed at every plan (constitution check) and every code review, and re-checked
before each pilot milestone.

**Version**: 1.0.0 | **Ratified**: 2026-11-14 | **Last Amended**: 2026-11-14
