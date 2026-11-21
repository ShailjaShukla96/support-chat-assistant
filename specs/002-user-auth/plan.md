# Implementation Plan: User Authentication for the Caramel AI Support Chat

**Branch**: `002-user-auth` | **Date**: 2026-11-21 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/002-user-auth/spec.md`; product source [002-prd-user-auth.md](../../002-prd-user-auth.md); governing rules [constitution v2.0.0](../../.specify/memory/constitution.md).

## Summary

Add individual accounts to the support chat in three rollout slices. Agents and admins get accounts with a password and an authenticator-app code, and every agent action made with an account is attributed to that account (Phase 1, alongside the shared `X-Agent-Token`, whose actions are recorded without an actor during the migration window). Customers get sign-up with email verification, sign-in, sign-out, password reset, session management, saved history, and session claiming behind a feature control (Phase 2). The shared token and `AGENT_API_TOKEN` are removed after the migration window (Phase 3). Anonymous chat is unchanged throughout.

Approach, from [research.md](research.md):

- **Identity store:** a new `user_account` table in the existing H2 database (standalone, not OIDC). Accounts are reached only through an `AccountRepository` seam so an OIDC source can replace it later.
- **Tokens:** short-lived JWT access tokens (HS256, 15 minutes) held in browser memory, plus an opaque refresh token (256-bit, stored only as a SHA-256 hash) in an `HttpOnly`, `Secure` cookie. Refresh rotates on every use. Reuse of a rotated token ends every session of that account, after a 10-second grace window (C5).
- **Revocation:** every authenticated request checks the session and account in one indexed lookup. This makes disable, sign-out, and password-reset revocation take effect immediately and lets role changes apply on the next request.
- **Passwords:** `DelegatingPasswordEncoder` with bcrypt. Argon2id is allowed by the constitution but needs BouncyCastle, which is not in the dependency set.
- **Second factor:** RFC 6238 TOTP for agents and admins, implemented in-house and tested against RFC vectors. The secret is encrypted at rest.
- **Authorization:** Spring Security filter chain by role, plus ownership checks in the services. A resource owned by someone else returns 404, the same as a missing one.
- **Escalations:** an admin assigns each escalation to an agent. Agents see only what is assigned to them, which follows FR-025 literally. Replies and status changes are stored and audited.
- **Email:** an `EmailSender` seam with the provider still undecided. Sends are asynchronous so that responses do not depend on whether an account exists.

## Technical Context

**Language/Version**: Java 21 (backend); TypeScript 5.x on Node 22 with React 19 (frontend)

**Primary Dependencies**: Spring Boot 4.0.9 (Spring Security 7.0.8 via the Boot BOM). New starters: `spring-boot-starter-security` and `spring-boot-starter-security-oauth2-resource-server` (brings Nimbus JOSE for HS256 JWT). Existing: Spring AI 2.0.2, Spring Data JDBC, H2. No other new libraries. TOTP and rate limiting are hand-written (research R5, R6). Both starter names were checked against the local Boot 4.0.9 BOM.

**Storage**: Embedded H2 (file mode). New tables for accounts, sessions, refresh tokens, one-time tokens, escalation replies, and audit events. Additive changes to `chat_session` and `escalation_request`. Schema stays in `schema.sql` with no migration tool (data-model.md, research R11).

**Testing**: JUnit 5, Mockito, Spring Boot Test, MockMvc with `spring-boot-starter-security-test` for the backend. Authorization tests cover every row of the access matrix, including cross-user attempts (Constitution IV). Vitest, React Testing Library, and msw for the frontend. The bundle-secret test is extended to the new variables. Accessibility checks (keyboard use, labelled fields, error text that does not disclose accounts) cover the auth screens (FR-039).

**Target Platform**: Linux server, single backend instance (as in v1). Evergreen browsers.

**Project Type**: Web application (Spring Boot service plus React/TypeScript UI).

**Performance Goals**: Sign-in completes in under 500 ms at p95 (SC-007). Authentication check adds under 5 ms at p95 (SC-008). Bcrypt cost is calibrated on target hardware so that these hold.

**Constraints**: Secrets (`AUTH_JWT_SIGNING_KEY`, `AUTH_TOTP_ENCRYPTION_KEY`, email credentials) come from the environment and never reach the bundle, logs, or responses (Constitution II). Passwords max 72 UTF-8 bytes, which is the bcrypt input limit (a plan addition; spec FR-003 sets only the 12-character minimum, so the spec should record this limit). Anonymous chat keeps working when auth or email is degraded (FR-014, SC-006). Auth events are logged with no emails, tokens, or passwords (Constitution V).

**Scale/Scope**: Pilot. Assumed low thousands of accounts, a few dozen agents and admins, one instance. Four new auth screens plus history, account settings, and a minimal agent queue in the frontend.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Checked against `.specify/memory/constitution.md` v2.0.0.

| Principle / rule | Pre-research | Post-design | How the design satisfies it |
|---|:-:|:-:|---|
| I. Grounded answers | PASS | PASS | Account details are not put in prompts. Personalization is out of scope (spec Assumptions, Supersession). |
| II. Secrets server-side | PASS | PASS | Three new env secrets (`AUTH_JWT_SIGNING_KEY`, `AUTH_TOTP_ENCRYPTION_KEY`, email credentials) are read only by backend config. Startup fails when a required key is missing. `.env.example` gets placeholders. `bundleSecrets` test extended. Refresh cookie is `HttpOnly` and `Secure`. |
| III. Human escalation always available | PASS | PASS | Escalation stays available anonymously and for customers. Escalation is not gated on sign-in or on email. Identity is attached when present. |
| IV. Test-first | PASS | PASS | Tasks will be test-first for password handling, token issuance and rotation, session claiming, and every access-matrix row with a cross-user case (quickstart §Verification). |
| V. Observability without leakage | PASS | PASS | Structured auth events with request ID and user ID only. `LogLeakTest` extended to cover emails, passwords, and all token types. Auth events kept 90 days, purged by a scheduled job (research R14). |
| VI. Identity, access, accountability | PASS | PASS | Anonymous chat unchanged. Server-side role and ownership checks on every endpoint. 404 for foreign resources. Individual accounts for agents and admins. Audit records for privileged actions. Refresh tokens hashed, rotated, `HttpOnly`, `Secure`. Access tokens in memory only. Sessions revocable on the server. Admins get no default access to customer conversations (FR-026, see C3). |
| Stack: Spring Boot, Spring AI, React + TS, gemma via AI Studio | PASS | PASS | No change. Auth uses Spring Security, JWT, and hashed opaque refresh tokens, which the constitution already lists. |
| Stack: "stateless resource server" | CONDITION | CONDITION | See C2. JWT signature validation stays stateless. Each request also runs one indexed session and account lookup, which Principle VI requires for server-side revocation. |
| No identity framework or token mechanism beyond the list | PASS | PASS | No OIDC, no OAuth server, no Argon2 library, no new token mechanism. TOTP and the rate limiter are hand-written, not new frameworks (R5, R6). |
| UI never calls the provider directly | PASS | PASS | UI calls only `/api/*`. |
| Session memory | PASS | PASS | Anonymous sessions stay session-only. Signed-in verified customers may persist history under Principle VI. Unverified sessions are not persisted. |
| Knowledge source | PASS | PASS | Unchanged. |
| Out of scope not built | PASS | PASS | No social SSO, no customer MFA, no account-changing actions from chat, no personalization by account data. |
| Performance targets (auth) | PASS | PASS | Measured in acceptance tests (quickstart §Performance). Bcrypt cost set from measurement. |
| Open Decisions resolved or tracked before dependent work | PASS | PASS | Each open decision below is either decided with a stated default or blocks only a named phase (Open decisions table). |
| Governance: owner approval of the v2.0.0 amendment | CONDITION | CONDITION | C1. Not recorded yet. Does not block planning. Blocks merging Phase 1 code. |

**Gate result**: No violations. Planning proceeds with the conditions below (C1 to C6):

- **C1 (Governance)**: The constitution v2.0.0 amendment (MAJOR bump, new Principle VI, lifted v1 out-of-scope items) needs project owner approval, which is not yet recorded. The spec's Assumptions and the constitution's SYNC IMPACT comment both say so. Planning is not blocked. Merging any Phase 1 code is. The SYNC IMPACT HTML comment also says it must be removed before commit.
- **C2 (Stateless wording)**: The constitution calls the resource server "stateless", but Principle VI requires server-side revocation, and FR-030 requires disablement to take effect immediately. This plan keeps JWT signature validation stateless and adds one indexed lookup per request. Owner to confirm this reading or amend the wording. The alternative is access tokens that live up to 15 minutes after disablement, which breaks FR-030.
- **C3 (Admin conversation access)**: PRD 002 §6 matrix gives admins access to all past sessions. Principle VI forbids a user reading another user's data. This plan follows the constitution and the spec's FR-026: admins see escalations, not unrelated conversations. Granting more needs an amendment. This is the most important decision to confirm.

## Open decisions

Each row is a decision the constitution requires to be settled before dependent work. "Blocks" names what cannot start until the owner confirms.

| # | Question (source) | Plan default | Blocks | Owner confirm |
|---|---|---|---|---|
| D1 | Identity source (PRD §11; constitution Open Decisions) | Standalone `user_account` in the chat database (R1) | Nothing new; OIDC would replace the repository later | Yes |
| D2 | Customer sign-in method (PRD §11) | Email and password only. Magic link deferred (R1) | Nothing | Yes |
| D3 | Agent and admin MFA at launch (FR-023, PRD FR-15) | Required for AGENT and ADMIN. Not offered to customers (customer MFA is out of scope) | Phase 1 release | Yes |
| D4 | Email provider (PRD §11) | `EmailSender` seam; dev outbox; no provider chosen (R10) | Phase 2 customer launch only. Phase 1 needs no email (activation links are shown once to the admin) | Yes, before Phase 2 |
| D5 | Admin access to customer conversations (spec Assumptions, FR-026) | No default access outside escalations (C3) | Admin screens for conversation search, if ever wanted | Yes, highest priority |
| D6 | Agent escalation visibility and assignment (spec Assumptions, FR-025) | Admin assigns and reassigns. Agents see only their assigned items. No self-claim (R12) | Phase 1 agent queue | Yes |
| D7 | Escalation status values (not in PRD) | `NEW`, `ACKNOWLEDGED`, `CLOSED` (R12) | Phase 1 agent status endpoint | Yes |
| D8 | How agent replies reach the customer (not in PRD or spec) | Stored and audited only. No delivery channel exists (v1 R7 notifier is logging only) | Phase 1 is shippable without it. Customer-visible replies need a channel decision | Yes |
| D9 | Escalations on account or history deletion (spec Assumptions, US8) | Escalations no agent has opened are deleted with the history. Opened ones keep the transcript with identity fields cleared (R13). This is a stricter threshold than the spec's "already received" (C6) | Phase 2 deletion | Yes |
| D10 | Compliance (GDPR, SOC 2) (spec Assumptions, FR-036) | Defaults from Principle V. Auth events kept 90 days (R14) | Phase 2 launch | Yes |
| D11 | Migration window length (FR-033, SC-003) | Ends when every agent and admin has an account and has signed in once with MFA. Date set by owner | Phase 3 | Yes |
| D12 | Bootstrap of the first admin (not in spec) | Startup command creates a first admin only when no admin exists, and prints a one-time activation link once (R18) | Phase 1 | Yes |
| D13 | Unverified sign-in and claiming (FR-002, FR-018) | Claiming is refused until the email is verified. The conversation stays claimable later (R7) | Phase 2 | Yes |
| D14 | Delivery of verification and reset when email is down vs. identical responses (spec Edge Cases vs. FR-011, SC-009) | Identical responses always. Sends are async and retried by the user. This departs from the edge-case wording. See C4 below | Phase 2 | Yes |

- **C4 (Edge case conflict)**: The spec's edge case says the sign-up, verification, and reset screens show "email cannot be sent right now" when the provider is down. FR-011 and SC-009 require the same response whether or not the account exists. If the provider is down, the failure would reveal existence. This plan keeps the identical response and surfaces the outage through the normal "check your email" screen plus a retry path. Owner to confirm.
- **C5 (Refresh reuse grace window)**: The spec's edge case treats a replaced continuation credential presented again as possible theft and ends all of the account's sessions. R3 adds a 10-second grace window in which a replaced token is refused but revokes nothing, so two tabs refreshing together are not signed out. The spec has no such window. Owner to confirm, or drop the window and accept that racing tabs sign out.
- **C6 (Escalation retention on deletion)**: The spec's assumption keeps the transcript of an escalation an agent has already received. R13 keeps only escalations an agent has opened and deletes the rest with the customer's history. Owner to confirm (D9).

## Project Structure

### Documentation (this feature)

```text
specs/002-user-auth/
├── plan.md                  # This file (/speckit-plan output)
├── research.md              # Phase 0 output: decisions R1–R18
├── data-model.md            # Phase 1 output: tables, state transitions
├── quickstart.md            # Phase 1 output: run and validation guide
├── contracts/
│   └── auth-api.yaml        # Phase 1 output: new and changed HTTP contract
├── checklists/
│   └── requirements.md      # Spec quality checklist (/speckit-specify)
└── tasks.md                 # Phase 2 output (/speckit-tasks; NOT created here)
```

### Source Code (repository root)

```text
.env.example                              # new placeholders (see research R16)

backend/
├── src/main/java/com/caramelai/support/
│   ├── config/
│   │   ├── SecurityConfig.java           # NEW: filter chain, stateless, route rules by role
│   │   ├── AuthProperties.java           # NEW: app.auth.* (feature flag, lifetimes, limits)
│   │   ├── CorsConfig.java               # CHANGED: credentials, DELETE, Authorization, X-Requested-With
│   │   ├── AgentTokenFilter.java         # CHANGED (Phase 1): becomes a shared-token authentication; removed in Phase 3
│   │   └── RequestIdFilter.java          # CHANGED: auth events carry request ID and user ID only
│   ├── auth/                             # NEW
│   │   ├── AuthController.java           # sign-in, refresh, sign-out, me, sign-up, verification, reset
│   │   ├── SignInService.java            # password check, lockout, delay, claim
│   │   ├── LockoutPolicy.java            # counters, lock windows, progressive delay
│   │   ├── TokenService.java             # JWT access tokens (HS256)
│   │   ├── RefreshTokenService.java      # issue, rotate, reuse detection, grace window
│   │   ├── PasswordPolicy.java           # length, byte limit, common-password list
│   │   ├── TotpService.java              # RFC 6238 codes, replay guard
│   │   ├── SecretCipher.java             # AES-256-GCM for TOTP secrets
│   │   ├── RateLimiter.java              # in-memory fixed windows per source and per account
│   │   └── AuthenticatedUser.java        # principal: user ID, role, session ID
│   ├── account/                          # NEW
│   │   ├── AccountService.java           # sign-up, verification, activation, disable, role change, delete
│   │   ├── UserAccount.java              # persistence records (see data-model)
│   │   └── AccountRepository.java        # the identity-store seam (R1)
│   ├── email/                            # NEW
│   │   ├── EmailSender.java              # interface (R10)
│   │   ├── OutboxEmailSender.java        # dev and test only: writes to backend/data/outbox
│   │   └── EmailMessages.java            # fixed templates: link only, no secrets
│   ├── audit/                            # NEW
│   │   ├── AuditService.java             # records privileged actions and auth events
│   │   ├── AuditEvent.java
│   │   └── AuditRetentionJob.java        # scheduled 90-day purge
│   ├── admin/                            # NEW
│   │   ├── AdminAccountController.java   # create, list, change role or status
│   │   ├── AdminEscalationController.java# assignment
│   │   └── BootstrapAdminRunner.java     # first-admin command (R18)
│   ├── agent/                            # NEW (Phase 1)
│   │   └── EscalationWorkflowService.java# assigned view, status, replies, audit
│   ├── api/
│   │   ├── AgentEscalationController.java# CHANGED: assigned-only for agents, actions added
│   │   ├── SessionController.java        # CHANGED: optional identity, owner set when eligible
│   │   ├── MessageController.java        # CHANGED: owner check (404)
│   │   ├── EscalationController.java     # CHANGED: identity attached when signed in
│   │   ├── RatingController.java         # CHANGED: owner check (404)
│   │   └── CustomerHistoryController.java# NEW: /api/me/sessions, /api/me/history, /api/me
│   ├── chat/ ...                         # unchanged except ownership hooks
│   ├── escalation/ ...                   # CHANGED: customer identity snapshot, assignment
│   └── persistence/                      # NEW records; ChatSession and EscalationRequest changed
├── src/main/resources/
│   ├── schema.sql                        # additive changes (data-model.md)
│   └── security/common-passwords.txt     # NEW: bundled common-password list (source chosen per R4)
└── src/test/java/com/caramelai/support/
    ├── unit/                              # token, TOTP vectors, lockout, rate limit, password policy, cipher
    ├── api/                               # MockMvc: every access-matrix row, cross-user 404s, auth endpoints
    ├── integration/                       # end-to-end sign-in → chat → escalate; LogLeakTest extended
    └── perf/                              # sign-in p95, auth-check p95 (tagged, run on demand)

frontend/
├── src/
│   ├── auth/                              # NEW
│   │   ├── AuthProvider.tsx               # auth context, silent refresh on load and on 401
│   │   └── accessTokenStore.ts            # in-memory token only
│   ├── components/                        # NEW: SignInForm, SignUpForm, ResetForms, SetPasswordForm,
│   │                                      #      HistoryList, AccountSettings, AgentQueue, EscalationDetail
│   ├── hooks/useChat.ts                   # CHANGED: optional identity; claim on sign-in
│   └── services/api.ts                    # CHANGED: the only module that calls fetch (credentials: include)
└── tests/                                 # auth flows and role screens with msw; bundleSecrets extended
```

**Structure Decision**: Keep the two-tier layout. Backend packages are split by responsibility so authorization can be tested without the chat model. Frontend keeps `services/api.ts` as the only `fetch` module, per CLAUDE.md. Nothing under `auth/` imports the model or prompt packages, so auth cannot leak into prompt building.

## Phases and slices

Each slice follows the spec's rollout. Within each slice, tests are written first (Constitution IV). Task-level ordering is for `/speckit-tasks`.

- **Slice 1 (Phase 1: agents)**: Schema for `user_account`, sessions, refresh tokens, activation, TOTP, sign-in, refresh, sign-out, sign-out-everywhere, and `/me`. Admin create, disable, and role change with last-admin guard. Escalation assignment, assigned-only queue, status, replies, audit. Shared token still accepted, with audit recorded as `SHARED_TOKEN` (no actor). Bootstrap admin command. Frontend: sign-in, set-password, MFA prompt, agent queue (minimal).
- **Slice 2 (Phase 2: customers, behind the feature flag)**: Sign-up, verification, resend, reset request and set-password, customer sign-in with claim, owner-scoped sessions and messages, customer identity on escalations, history and account deletion, feature flag, customer screens. Email provider (D4) must be chosen before this slice ships. The dev outbox covers tests until then.
- **Slice 3 (Phase 3: cleanup)**: Remove `AgentTokenFilter`, `AGENT_API_TOKEN`, the `X-Agent-Token` CORS entry, and the old contract entries. Update the v1 artifacts listed under Supersession.

## Complexity Tracking

| Addition | Why Needed | Simpler Alternative Rejected Because |
|---|---|---|
| Per-request session and account lookup, on top of JWT signature validation | FR-030 (disable takes effect immediately), FR-007, Principle VI (server-side revocation), FR-034 and FR-029 (role change on next request) | Stateless-only JWT leaves a disabled agent able to act for up to 15 minutes. Longer tokens make it worse (C2) |
| `sign_in_session` and `refresh_token` tables | Sign-out, sign-out everywhere, and reuse detection need server-side state; spec entity "Sign-In Session" | Refresh tokens alone cannot end all sessions of one device family or report reuse per family |
| Hand-written RFC 6238 TOTP | Needed for FR-023. No TOTP library is in the local dependency cache, and adding one is a new dependency to review | A TOTP library (e.g., dev.samstevens.totp) is a valid alternative. Reviewed against RFC 6238 vectors either way |
| Hand-written in-memory rate limiter | FR-013 (per source and per account). A single backend instance is assumed (v1 plan) | Bucket4j or a shared store adds a dependency or infrastructure that the pilot does not have |
| `audit_event` table | FR-027, FR-031, FR-035, SC-003: privileged actions must be queryable and attributable. Logs alone are not queryable for this | Structured logs only: harder to retain for a bounded period and to answer "who changed this" |
| `escalation_reply`, assignment and status fields | FR-025, FR-027 and US1 require reply and status actions on assigned escalations | Reusing `message` would mix customer chat with agent replies and leak agent text into prompts |
| Dev `OutboxEmailSender` | Email provider undecided (D4). Tests need to read verification and reset links | A mock per test cannot cover the link-to-token path end to end |
| Async email send | FR-011 and SC-009 require identical responses whether or not an account exists (C4) | Synchronous send would reveal existence through timing or failure |

## Supersession of v1 artifacts

The constitution's migration plan requires these to be marked superseded or updated in the tasks for each slice. This plan does not edit them.

- `specs/001-support-chat-assistant/spec.md`: FR-017 and the out-of-scope personalization line (superseded by spec 002).
- `specs/001-support-chat-assistant/plan.md`: constitution-check row for personalization and the shared token (superseded; v1 record kept).
- `specs/001-support-chat-assistant/data-model.md`: `userRef` stays but is no longer an identity (see R17).
- `specs/001-support-chat-assistant/contracts/chat-api.yaml`: agent endpoints (`X-Agent-Token`). Replaced by `auth-api.yaml`.
- Code: `AgentTokenFilter` (Slice 3 removal), `CorsConfig` (credentials and methods), `EscalationApiTest` (add token-to-account cases in Slice 1; remove token cases in Slice 3).
