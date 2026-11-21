# Research: User Authentication for the Caramel AI Support Chat

**Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)

Each entry gives the decision, the reason, and the alternatives. Entries marked **(owner)** are defaults the owner must confirm (see plan.md Open decisions).

## Environment facts checked

- The backend is on Spring Boot 4.0.9 (`backend/pom.xml`), which manages Spring Security 7.0.8. The local Maven cache has the Boot 4.0.9 BOM, which lists `spring-boot-starter-security`, `spring-boot-starter-security-oauth2-resource-server`, and `spring-boot-starter-security-test`. These are the starter names to use.
- The local cache has no BouncyCastle and no Nimbus artifacts. Spring Security's Argon2 encoder needs BouncyCastle, so Argon2id would add a dependency. The JWT library arrives through the resource-server starter.
- The existing agent controller is read-only (`AgentEscalationController`: list and get). Status change and reply do not exist yet, so Slice 1 adds them.
- `chat_session.user_ref` is a v1 host-site ID, passed by the client and never used for identity.
- `CorsConfig` allows only `GET`, `POST`, `PUT` and does not allow credentials or `Authorization`.

## R1. Identity store (PRD §11, D1) (owner)

- **Decision**: A standalone `user_account` table in the chat database. No OIDC. Accounts are accessed only through `AccountRepository`, so an OIDC or Caramel AI account source can replace it later.
- **Rationale**: The spec's Assumptions and the PRD's table list both point to new tables. No Caramel AI account system is integrated with the chat today. A standalone store can ship in Phase 1 without an external dependency.
- **Alternatives**: Caramel AI's existing accounts with OIDC. Rejected for now because no integration exists and the spec's open decision is still unresolved. Revisit when the owner decides.
- **Customer sign-in method (D2)**: Email and password only. Magic link is deferred, as the spec's Assumptions say.

## R2. Access tokens (FR-005, Principle VI)

- **Decision**: JWT access tokens signed with HS256 by a 256-bit key from `AUTH_JWT_SIGNING_KEY`. Lifetime 15 minutes. Claims: `sub` (user ID), `sid` (session ID), `iat`, `exp`, `iss`, `aud`. The role is not in the token. Each request reads the current role from the account row (R7).
- **Validation**: `spring-boot-starter-security-oauth2-resource-server` with a `NimbusJwtDecoder`. Startup fails when the key is missing or shorter than 32 bytes.
- **Rationale**: The PRD and constitution require JWT access tokens. HS256 needs one shared secret, which this single-instance backend already holds. The role is read per request so that role changes apply on the next request (FR-029, spec US2 AC3).
- **Alternatives**: RS256 or ES256 with a key pair. Not needed while only this service verifies tokens. Opaque access tokens with a lookup on every request. These would match the per-request check, but the constitution names JWT, so this would need an amendment.
- **Key rotation**: Out of scope for this plan. The key is replaced by restarting with a new value, which signs out everyone.

## R3. Refresh tokens and session families (FR-005, FR-007, FR-008, Edge Cases)

- **Decision**:
  - Opaque, 256 bits from `SecureRandom`, base64url. Only the SHA-256 hash is stored (`refresh_token.token_hash`, unique).
  - Each sign-in creates one `sign_in_session` with an absolute expiry of 7 days from sign-in. Each refresh inside that session replaces its `refresh_token` row (rotation). The old row gets `rotated_at`.
  - The refresh cookie is `caramel_refresh`, `HttpOnly`, `Secure`, `SameSite=Lax`, `Path=/api/auth`.
  - **Reuse detection**: a rotated token presented after a 10-second grace window revokes the whole session and every other session of the account (reason `TOKEN_REUSE`). This is the spec's "treated as possible theft" rule.
  - **Grace window**: a rotated token presented within 10 seconds is refused with 401 but does not revoke anything. This covers two tabs refreshing at the same moment (spec Edge Cases, tab sign-out) without signing the user out.
- **Constant-time note**: Lookup is by the hash through a unique index. The comparison happens in the database, and the attacker never sees the stored value. This meets the PRD requirement for constant-time token handling without a custom byte comparison.
- **Alternatives**: Sliding expiry. Rejected because the spec sets a fixed 7-day continuation credential. No grace window, which would sign out two tabs racing to refresh. Rejected because it would conflict with the spec's own tab-sign-out edge case.

## R4. Password hashing and policy (FR-003, Principle VI) (owner)

- **Decision**: `DelegatingPasswordEncoder` with bcrypt as the default id (`{bcrypt}`). Cost factor set by measurement on the target host, with a starting value of 10. The timing target is sign-in under 500 ms at p95 including MFA check (SC-007).
- **Policy**: At least 12 characters. At most 72 UTF-8 bytes, because bcrypt ignores bytes after 72. Checked against a bundled common-password list (`security/common-passwords.txt`). The list source is a choice for the owner. Until chosen, the plan uses a published top-10,000 list whose license is checked during implementation.
- **Unknown accounts**: Sign-in with an unknown email still runs a bcrypt check against a dummy hash at the same cost, so the response time does not reveal existence.
- **Alternatives**: Argon2id. Allowed by the constitution, but needs BouncyCastle. Not chosen because it adds a dependency and bcrypt meets OWASP ASVS Level 2 at the chosen cost.

## R5. Second factor for agents and admins (FR-023, D3) (owner)

- **Decision**: RFC 6238 TOTP, HMAC-SHA1, 30-second step, 6 digits, accept one step either side. Implemented in `TotpService` with `javax.crypto.Mac`.
  - The secret is 160 bits from `SecureRandom`. It is stored AES-256-GCM encrypted (`SecretCipher`), keyed by `AUTH_TOTP_ENCRYPTION_KEY`, with a random 12-byte IV per record.
  - Replay guard: `user_account.mfa_last_step` records the last accepted time step. A code for the same or an earlier step is refused.
  - **Enrollment**: On activation the server generates the secret and returns the `otpauth://` URI once. Enrollment is confirmed by the first valid code at sign-in, which sets `mfa_enrolled_at`. There is no separate enrollment endpoint, and no pre-authentication ticket is needed.
- **Verification**: RFC 6238 Appendix B test vectors for SHA-1, plus replay and window tests.
- **Alternatives**: `dev.samstevens.totp` or another library. A reasonable choice, and it reduces hand-written crypto. Not chosen because it is a new dependency that is not in the local cache. The owner can ask for the library instead; the interface is the same.
- **Customer MFA**: Not built (out of scope).

## R6. Rate limiting and lockout (FR-012, FR-013)

- **Decision**:
  - **Lockout (persisted)**: `user_account.failed_sign_in_count`, `next_attempt_at`, and `locked_until`. From the 2nd consecutive failure, `next_attempt_at` is set to now + 2^(n−2) seconds (1, 2, 4, 8 …). At 5 failures, `locked_until` is now + 15 minutes. A success resets the counters. The lockout is persisted so that it survives restarts.
  - **Per-account throttle responses are invisible**: A sign-in that arrives during a delay or lockout gets the same 401 body as a wrong password. The bcrypt check still runs, so the timing matches too. The lockout is recorded in the audit log (FR-035).
  - **Per-source limits (in memory)**: fixed windows keyed by client IP. Sign-in 20 per minute. Sign-up 10 per hour. Reset request 5 per hour. Exceeding returns 429 with a generic body that does not name an account.
  - **Per-account limits**: sign-up and reset requests are limited per target email (hashed key in memory), 3 per hour. Responses stay identical, so the limit is silent.
  - Values are provisional. The spec leaves ceilings to planning.
- **Alternatives**: Bucket4j or Redis for shared limits. Rejected because the pilot runs one instance. The limiter is a single class behind an interface, so a shared store can replace it at multi-instance scale.

## R7. Chat session ownership and claiming (FR-002, FR-016 to FR-018, D13) (owner)

- **Decision**:
  - `chat_session.user_id` is set when a signed-in, email-verified customer creates a session (`POST /api/sessions`) while the customer feature flag is on. Otherwise the session is anonymous, as in v1.
  - **Owner check**: any read, message, rating, or escalation on an owned session requires that user, and a mismatch returns 404 (`SessionNotFoundException`). Anonymous sessions keep the v1 rule: whoever holds the session ID may use it.
  - **Claim** happens at sign-in. Sign-in takes an optional `claimSessionId`. The claim succeeds only if: the password and any TOTP check pass, the account is a verified customer, the session exists, `user_id` is null, and no earlier claim exists. The update is one conditional statement (`UPDATE … WHERE user_id IS NULL`), so two claims of one session cannot both succeed. The result is returned in the sign-in response as `claimed: true|false` with a fixed reason.
  - **Unverified accounts (D13)**: Claiming is refused until the email is verified. The reason is `email_not_verified`. The anonymous session stays claimable later. This follows FR-002, which says conversations are not saved to an account until verified.
  - **Sessions created while unverified** are not saved retroactively. This is the conservative reading of FR-002 and needs owner confirmation with D13.
  - Claim on a foreign or missing session returns the same `claimed: false` response with no reason that reveals existence (FR-017 analogue, spec US6 AC2).
- **Alternatives**: Claim by a one-time claim code shown in the chat. Adds a flow the spec does not ask for. The claim is authorized by possession of the session ID plus the same action's sign-in, as FR-018 requires.

## R8. Shared agent token during migration (FR-033, Constitution VI)

- **Decision**: In Slice 1 the current `AgentTokenFilter` becomes one more way to authenticate to `/api/agent/**`. It produces a principal with role `AGENT` and `actor_kind = SHARED_TOKEN`. Audit events from it have no actor user ID. Those events are reported as unattributed until Phase 3. The filter is removed in Slice 3, and then requests with the header are refused (SC-011).
- **Admin scope**: The shared token sees all escalations, as it does in v1. It cannot manage accounts. Those endpoints require a real ADMIN account.
- **Alternatives**: Remove the shared token in Phase 1. Rejected because the rollout says both are accepted in Phase 1 (PRD §10).

## R9. CSRF on cookie-authenticated endpoints (PRD NFR Security)

- **Decision**: The bearer-token endpoints are not cookie-authenticated, so CSRF protection is disabled for them. The cookie-authenticated endpoints (`/api/auth/refresh`, `/api/auth/sign-out`) require two things: the request has an `Origin` header equal to the configured frontend origin, and it has `X-Requested-With: caramel-chat`. A non-simple header forces a CORS preflight, which a cross-site form cannot pass.
- **SameSite=Lax** already blocks cross-site POST cookies. The explicit checks are a second layer and are tested.
- **CORS**: `allowCredentials(true)`, one origin from `app.cors.allowed-origin`, methods `GET, POST, PUT, PATCH, DELETE`, headers `Content-Type, Authorization, X-Requested-With, X-Request-Id, X-Agent-Token`. No wildcard.

## R10. Email delivery (D4, FR-038, FR-011) (owner)

- **Decision**: An `EmailSender` interface with one call (`send(template, recipientRef, link)`). The provider is not chosen. Until it is, the only implementation is `OutboxEmailSender`, which writes messages to `backend/data/outbox/` (git-ignored) when the `dev` profile is active, and is used by tests. The production profile fails at startup when provider credentials are missing, once Phase 2 is enabled.
  - Messages contain only one single-use link (FR-038). Tokens sit in the URL fragment (`#token=…`), so they are not sent to servers as query strings or written to proxy logs.
  - **Async send**: Sign-up, verification resend, and reset requests return before the send. A failure is recorded as a fixed-vocabulary audit event with no address. Responses do not vary with account existence (FR-011, SC-009).
- **Owner notice on duplicate sign-up**: The existing owner gets a notice with fixed wording. The wording is for planning to confirm (spec Assumptions).
- **Alternatives**: Synchronous send with an error on failure. Rejected because the failure reveals that an account exists (C4).

## R11. Persistence changes (Constitution, CLAUDE.md "no migration tool")

- **Decision**: Keep `schema.sql` as the schema source. New tables use `CREATE TABLE IF NOT EXISTS`. Column additions use `ALTER TABLE … ADD COLUMN IF NOT EXISTS`. The `escalation_request.session_id` NOT NULL constraint becomes nullable (`ALTER TABLE … ALTER COLUMN … SET NULL`) so deleted history can keep escalation rows (R13). Each new record is written through `Store`, as CLAUDE.md requires.
- **Existing dev database**: The v1 file at `backend/data/support` predates this change. The first Slice 1 task deletes it or applies the ALTER statements manually, and the quickstart says which. If pilot data must be kept, the owner must say so before the change.
- **Alternatives**: Flyway. Rejected because the project has no migration tool, and CLAUDE.md says so explicitly. Adding one would be a new dependency for a single schema change.

## R12. Escalation assignment, status, and replies (FR-024 to FR-028, D6, D7, D8) (owner)

- **Decision**:
  - **Assignment**: Admins set `escalation_request.assigned_agent_id` through `PUT /api/admin/escalations/{id}/assignment`. Reassignment replaces it. Each change is audited. Agents see only escalations assigned to them (FR-025). Admins see all (FR-026, escalations only).
  - **Status**: `NEW` → `ACKNOWLEDGED` (set on the agent's first open or first status change) → `CLOSED`. A `CLOSED` escalation can be reopened only by an admin. Status values are not in the PRD, so this is a default.
  - **Replies**: Stored in `escalation_reply` with the author's user ID and time. Max 2,000 characters, like customer messages. Replies never enter the chat prompt (Principle I, and the prompt is built from `message` only).
  - **Customer delivery (D8)**: Not decided. Replies are stored and audited only. A channel is needed before customers can see them (spec does not say).
  - **Visibility of unassigned items**: Hidden from agents. Self-claim would need FR-025 amended.
- **Alternatives**: Agents self-claim from an unassigned queue. Better for throughput. Needs a spec amendment, so it is not the default.

## R13. Deletion semantics (FR-020, FR-021, D9) (owner)

- **Decision**:
  - **Delete history**: Delete the user's sessions, messages, and ratings. Escalations for those sessions are handled as below.
  - **Delete account**: Requires the current password in the request body (guards against a stolen session). Revokes every session, deletes the account row, and runs the history deletion. Audit events keep the user ID only, for the retention period (R14).
  - **Escalations**: Unopened (`first_opened_at` null) escalations are deleted with the history, because their transcript is a copy of the customer's messages. Opened ones keep the transcript, with `session_id`, `customer_user_id`, and `customer_email` cleared (identity removed per spec Assumptions).
  - This goes further than the spec's Assumption, which covers only escalations "already received". Owner to confirm (D9).
- **Alternatives**: Keep all escalation transcripts. Rejected because it keeps a customer's messages after they delete their history, which FR-020 forbids in spirit.

## R14. Audit events and retention (Principle V, FR-035, FR-036)

- **Decision**: One `audit_event` table for privileged actions and auth events (fixed `event_type` vocabulary, optional `detail` from a fixed list such as `role=ADMIN`). Each row has the request ID, actor user ID or null, and the actor kind. It never holds emails, tokens, or passwords.
- The same events are also written as structured log lines with the request ID and user ID.
- **Retention**: 90 days by default (spec Assumptions, owner to confirm D10). `AuditRetentionJob` runs daily and deletes rows older than that.
- **Alternatives**: Logs only. Rejected because agent attribution has to be queryable (SC-003) and must outlive log rotation.

## R15. Frontend auth

- **Decision**:
  - `AuthProvider` holds the access token in React memory only (`accessTokenStore.ts`). Nothing goes to `localStorage` or `sessionStorage` (FR-006).
  - On load, the provider calls `POST /api/auth/refresh` once to restore a session from the cookie. It refreshes shortly before expiry and on any 401, retrying once.
  - `services/api.ts` remains the only `fetch` caller, now with `credentials: "include"`, the `Authorization` header when a token exists, and `X-Requested-With` on cookie endpoints.
  - Screens are sign-in, sign-up, verification notice, reset request, set-password (for both reset and activation, with a TOTP field for agents), history list, reopen-and-continue, account settings (sign out, sign out everywhere, delete history, delete account), and a minimal agent queue.
  - Error copy never says whether an email is registered.
- **Tests**: Vitest and RTL with msw. Role screens are rendered per role. The bundle secret test covers the new variables.

## R16. Configuration and environment

- **Decision**: New variables, all placeholders in `.env.example`:

  | Variable | Purpose | Required |
  |---|---|---|
  | `AUTH_JWT_SIGNING_KEY` | HS256 key, base64, 32+ bytes | Yes; startup fails when missing |
  | `AUTH_TOTP_ENCRYPTION_KEY` | AES-256 key for TOTP secrets, base64, 32 bytes | Yes once agents exist |
  | `AUTH_CUSTOMER_SIGNIN_ENABLED` | Customer feature control (FR-037) | No; default `false` |
  | `AUTH_BOOTSTRAP_ADMIN_EMAIL` | First-admin command, used once (R18) | No |
  | `APP_PUBLIC_BASE_URL` | Base for activation and reset links (frontend origin) | Yes |
  | `EMAIL_PROVIDER_API_KEY`, `EMAIL_FROM_ADDRESS` | Email provider (D4) | Phase 2 only; empty uses the dev outbox |

  `AGENT_API_TOKEN` stays until Slice 3.
- **Config binding**: `AuthProperties` binds `app.auth.*`. Lifetimes, limits, and lockout values are defined there with the defaults above, so tests can override them.

## R17. Host-site `userRef`

- **Decision**: `chat_session.user_ref` stays as v1 for compatibility. It is never an identity: no authorization, ownership, or claim uses it. Documentation says so. Account identity comes only from `user_id`.
- **Alternatives**: Remove the column. Rejected for now because host sites already send it; removal would be a separate change.

## R18. First admin bootstrap (D12) (owner)

- **Decision**: A startup `CommandLineRunner` creates one `ADMIN` account when `AUTH_BOOTSTRAP_ADMIN_EMAIL` is set and no admin exists. It creates a pending account and prints a one-time activation link to the console once. It is never written to a structured log. It does nothing when an admin already exists, so the variable can be left in place.
- **Alternatives**: A SQL insert by hand. Rejected because the bootstrap admin still needs the activation flow to set a password and enroll TOTP. A hand-run SQL insert would bypass that.
