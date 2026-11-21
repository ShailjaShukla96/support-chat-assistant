# Tasks: User Authentication for the Caramel AI Support Chat

**Branch**: `002-user-auth`
**Inputs**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/auth-api.yaml](contracts/auth-api.yaml), [quickstart.md](quickstart.md)

**Tests**: Included, and written first. Constitution IV and plan Technical Context require it. Each test task comes before the implementation it covers and must fail before that implementation is written.

**Organization**: Phase 1 and Phase 2 are shared. Phases 3 to 4 are Slice 1 (agents). Phases 5 to 10 are Slice 2 (customers, behind `AUTH_CUSTOMER_SIGNIN_ENABLED`). Phase 11 is Slice 3 (cleanup). Phase 12 is polish. Phase order inside Slice 2 follows spec priority, with P2 stories first.

## Format

`- [ ] Tnnn [P?] [USn?] Description with file path`

- `[P]`: can run in parallel (different files, no dependency on an unfinished task).
- `[USn]`: user story from spec.md. Setup, foundational, and polish tasks have no story label.
- `[BLOCKED: X]`: do not start until X is resolved. X is an open decision (D1 to D14), a constitution condition (C1 to C6), or the owner question DB. See the gate table.

## Gates and blocked tasks

Read this before starting. Tasks that are not tagged may proceed.

| Tag | Source | Effect |
|---|---|---|
| C1 | Constitution v2.0.0 owner approval (plan C1) | Gate. No Phase 3 or later code is merged until approval is recorded. Development may proceed. |
| D3 | Agent and admin MFA at launch (plan D3) | Gate. Phase 4 cannot be released until the owner confirms. |
| D4 | Email provider (plan D4) | Blocks the production provider only (T102). The dev outbox proceeds. |
| D6 | Escalation assignment and assigned-only queue (plan D6) | Blocks the assignment, queue filtering, and reply authorization tasks. |
| D7 | Escalation status values (plan D7) | Blocks the status endpoint (T085). |
| D8 | Customer reply delivery (plan D8) | Blocks customer-visible reply delivery. Reply storage proceeds. |
| D9 and C6 | Escalation rule on deletion (plan D9, C6) | Blocks escalation handling in deletion. |
| D10 | Compliance and retention (plan D10) | Gate. Phase 2 launch cannot proceed until confirmed. |
| D11 | Migration window end date (plan D11) | Blocks Phase 11. |
| D12 | First-admin bootstrap (plan D12) | Blocks the bootstrap runner (T048). |
| D13 | Claiming for unverified accounts (plan D13) | Blocks the email-verified claim gate (T146). |
| D14 and C4 | Identical responses during email outage (plan D14, C4) | Blocks only the outage-message behavior (T119). Identical responses are required now by FR-011. |
| C5 | Refresh reuse grace window (plan C5) | Blocks only the grace-window behavior (T063 and T077). Reuse revocation after the window proceeds. |
| DB | Owner question: does `backend/data/support*` hold pilot data? (research R11) | Blocks T005. |

**Not built, so no tasks**: admin browsing of customer conversations (C3, D5, FR-026), and customer multi-factor authentication (out of scope).

---

## Phase 1: Setup (shared infrastructure)

**Purpose**: Dependencies, configuration placeholders, and secret-handling checks.

- [ ] T001 Add `spring-boot-starter-security` and `spring-boot-starter-security-oauth2-resource-server` to `backend/pom.xml`, and `spring-boot-starter-security-test` with test scope. Versions come from the Boot 4.0.9 BOM (research, Environment facts). Do not add BouncyCastle, Nimbus, or a TOTP library directly.
- [ ] T002 [P] Add placeholder entries to `.env.example` for `AUTH_JWT_SIGNING_KEY`, `AUTH_TOTP_ENCRYPTION_KEY`, `AUTH_CUSTOMER_SIGNIN_ENABLED=false`, `AUTH_BOOTSTRAP_ADMIN_EMAIL`, `APP_PUBLIC_BASE_URL=http://localhost:5173`, `EMAIL_PROVIDER_API_KEY=`, and `EMAIL_FROM_ADDRESS=`. Use placeholders only (Constitution II; research R16).
- [ ] T003 [P] Add `backend/data/outbox/` to the root `.gitignore`. The dev email outbox holds single-use links and must not be committed (research R10).
- [ ] T004 [P] Extend `frontend/tests/bundleSecrets.test.ts` to scan `dist/` for the values of `AUTH_JWT_SIGNING_KEY`, `AUTH_TOTP_ENCRYPTION_KEY`, and `EMAIL_PROVIDER_API_KEY` (quickstart, Checks that apply to every slice).
- [ ] T005 [BLOCKED: DB] Before the first run, either delete `backend/data/support*` or apply the `ALTER` statements from data-model.md (research R11). Do not delete anything until the owner says whether pilot data must be kept.
- [ ] T006 [P] Add `backend/src/main/resources/security/common-passwords.txt`, the bundled common-password list (research R4). Check the license of the chosen published top-10,000 list and record the source in the file header. The owner may replace the source later.

---

## Phase 2: Foundational (blocks all user stories)

**Purpose**: Shared auth building blocks. No user story starts until this phase is complete.

**Tests first (must fail before the code below exists)**

- [ ] T007 [P] Write `backend/src/test/java/com/caramelai/support/unit/PasswordPolicyTest.java`. Cases: under 12 characters is refused; a 25-character string of `é` (50 UTF-8 bytes) is accepted; 37 `é` (74 bytes) is refused for exceeding "at most 72 UTF-8 bytes"; an entry from `security/common-passwords.txt` is refused; the message names the rule and no account (FR-003, data-model validation).
- [ ] T008 [P] Write `backend/src/test/java/com/caramelai/support/unit/TotpServiceTest.java` with the RFC 6238 Appendix B SHA-1 vectors (ASCII secret `12345678901234567890`, time 59, 6-digit code `287082`). Add cases: a code from the same time step as the last accepted one is refused (replay); a code one step before or after is accepted (research R5).
- [ ] T009 [P] Write `backend/src/test/java/com/caramelai/support/unit/SecretCipherTest.java`. Cases: AES-256-GCM round trip; the same plaintext encrypts differently each time (fresh 12-byte IV); a tampered ciphertext is refused; a wrong key is refused (research R5).
- [ ] T010 [P] Write `backend/src/test/java/com/caramelai/support/unit/LockoutPolicyTest.java` with an injected clock. Cases: failure 1 sets no delay; failure 2 sets `nextAttemptAt` to now + 1 s; failure 3 to +2 s; failure 4 to +4 s; failure 5 sets `lockedUntil` to now + 15 min; success resets `failedSignInCount` (research R6, FR-012).
- [ ] T011 [P] Write `backend/src/test/java/com/caramelai/support/unit/RateLimiterTest.java`. Fixed windows: sign-in 20 per minute per IP, sign-up 10 per hour per IP, reset request 5 per hour per IP, and 3 per hour per hashed target email. The 21st sign-in in a minute is refused (research R6, FR-013).
- [ ] T012 [P] Write `backend/src/test/java/com/caramelai/support/unit/TokenServiceTest.java`. Cases: HS256; claims `sub`, `sid`, `iat`, `exp`, `iss`, `aud` present; `exp - iat` is 15 minutes; no `role` claim (research R2). Also write `backend/src/test/java/com/caramelai/support/unit/AuthPropertiesValidationTest.java`: startup fails when `AUTH_JWT_SIGNING_KEY` is missing or under 32 bytes, and when `AUTH_TOTP_ENCRYPTION_KEY` is not 32 bytes (research R16, quickstart Prerequisites).
- [ ] T013 [P] Write `backend/src/test/java/com/caramelai/support/unit/RefreshTokenServiceTest.java`. Cases: the issued token is 256 bits, base64url; only its SHA-256 hash is stored in `refresh_token.token_hash`; rotation sets `rotated_at` on the old row and creates a new row; `sign_in_session.expires_at` is created + 7 days and does not slide (research R3). The grace-window cases are in T063, and the behavior is in T077.
- [ ] T014 [P] Extend `backend/src/test/java/com/caramelai/support/integration/LogLeakTest.java`. It fails if any log line or `audit_event.detail` contains an email, password, access token, refresh token, activation or reset token, TOTP secret, or signing key (FR-035, SC-010, Constitution V).
- [ ] T015 [P] Write `backend/src/test/java/com/caramelai/support/api/SecurityConfigTest.java` (MockMvc). Cases: anonymous `GET /api/agent/escalations` returns 401 as problem+json; `GET /api/auth/config` is public; an unknown `/api/**` path is not public (contract, Auth and Agent tags).
- [ ] T016 [P] Write `backend/src/test/java/com/caramelai/support/api/CorsAuthTest.java`. Cases: a request with credentials from the allowed origin gets `Access-Control-Allow-Credentials: true`; `Authorization` and `X-Requested-With` are allowed headers; `DELETE` and `PATCH` are allowed methods; a wildcard origin is refused (research R9).
- [ ] T017 [P] Write `frontend/tests/auth/accessTokenStore.test.ts`. Cases: the access token is held in memory only; `localStorage` and `sessionStorage` are never written (FR-006); the token is cleared on sign-out.
- [ ] T018 [P] Write `frontend/tests/auth/api.credentials.test.ts`. Cases: every `fetch` from `services/api.ts` sends `credentials: "include"`; cookie endpoints send `X-Requested-With: caramel-chat`; the `Authorization` header is added only when a token exists (research R15).

**Implementation**

- [ ] T019 Create `backend/src/main/java/com/caramelai/support/config/AuthProperties.java` binding `app.auth.*`. Defaults: access token 15 min; continuation 7 days; reset link 30 min; activation link 24 h; email verification link 24 h; lockout after 5 failures for 15 min; bcrypt cost 10; reuse grace window 10 s (value wired in T077); rate limits from T011. Startup validation makes T012 pass. Defaults must match research R2, R3, R6, and R16 so tests can override them.
- [ ] T020 Add the `AUTH_*` and `APP_PUBLIC_BASE_URL` keys to `backend/src/main/resources/application.yml`, mapped to `app.auth.*` and the environment variables. Do not add any default for secret keys.
- [ ] T021 Update `backend/src/main/resources/schema.sql` with `CREATE TABLE IF NOT EXISTS user_account`. Quote the rules from data-model.md verbatim. `email`: "string(320). Required, unique, normalized." `role`: "enum CUSTOMER, AGENT, ADMIN. Required. Read on every request (R2)." `status`: "enum ACTIVE, DISABLED. Required. `DISABLED` ends every session at once (FR-030)." `passwordHash`: "string(100), nullable. `{bcrypt}…`. Null until a password is set." `mfaSecretEncrypted`: "string(255), nullable. AES-256-GCM ciphertext of the TOTP secret. Required for `AGENT` and `ADMIN`." `mfaLastStep`: "long, nullable. Last accepted TOTP time step. Rejects replay." `failedSignInCount`: "integer. Consecutive failures. Reset on success." `createdByUserId`: "UUID, nullable." Add the unique index `user_account(email)`.
- [ ] T022 [P] Update `backend/src/main/resources/schema.sql` with `CREATE TABLE IF NOT EXISTS` for `sign_in_session`, `refresh_token`, `password_reset_token`, and `email_verification_token`, per data-model.md. Quote the rules verbatim: `refresh_token.token_hash` "SHA-256 hex of the opaque token. Unique."; `password_reset_token.purpose` "enum `RESET`, `ACTIVATION`. Required."; `consumedAt` "Set on use. Also set on supersession." Add the indexes listed under Schema notes: `sign_in_session(user_id)`, `refresh_token(token_hash)` unique, `refresh_token(session_id)`, `password_reset_token(token_hash)` unique, `(user_id, purpose)`, and `email_verification_token(token_hash)` unique.
- [ ] T023 [P] Update `backend/src/main/resources/schema.sql` with `CREATE TABLE IF NOT EXISTS audit_event` and `escalation_reply`. Quote the rules verbatim: `audit_event.detail` "string(128), nullable. From a fixed list only … Never an email, token, or password."; `escalation_reply.text` "CLOB. 1 to 2,000 characters after trimming."; `escalation_reply.authorKind` "enum `USER`, `SHARED_TOKEN`. Required." Add the index `audit_event(occurred_at)`.
- [ ] T024 Update `backend/src/main/resources/schema.sql` with `ALTER TABLE … ADD COLUMN IF NOT EXISTS` for `chat_session.user_id` (FK, `ON DELETE SET NULL`) and `chat_session.claimed_at`. For `escalation_request`, add `customer_user_id`, `customer_email`, `assigned_agent_id`, `assigned_at`, and `first_opened_at`, and change `session_id` to nullable (`ALTER COLUMN … SET NULL`) per research R11. Add the indexes `chat_session(user_id, created_at)` and `escalation_request(assigned_agent_id, created_at)`.
- [ ] T025 Create `backend/src/main/java/com/caramelai/support/account/UserAccount.java` (record), `account/AccountRepository.java` (interface, the identity-store seam from research R1), and `account/JdbcAccountRepository.java`. The JDBC implementation writes through `persistence/Store` (`JdbcAggregateTemplate`), not `CrudRepository.save`, because entity IDs are application-generated UUIDs (CLAUDE.md).
- [ ] T026 Create `backend/src/main/java/com/caramelai/support/auth/PasswordPolicy.java` to make T007 pass. Rules: at least 12 characters; at most 72 UTF-8 bytes; not on `security/common-passwords.txt`.
- [ ] T027 Create `backend/src/main/java/com/caramelai/support/config/PasswordEncoderConfig.java`. It exposes a `DelegatingPasswordEncoder` with `{bcrypt}` as the default and the cost from `AuthProperties`. It also defines a dummy bcrypt hash constant for unknown accounts so sign-in takes the same time whether or not the email exists (research R4).
- [ ] T028 Create `backend/src/main/java/com/caramelai/support/auth/SecretCipher.java`. AES-256-GCM with a random 12-byte IV per record. The key comes from `AUTH_TOTP_ENCRYPTION_KEY`. Makes T009 pass.
- [ ] T029 Create `backend/src/main/java/com/caramelai/support/auth/TotpService.java`. RFC 6238, HMAC-SHA1, 30-second step, 6 digits, accept ±1 step. The secret is 160 bits from `SecureRandom`. The replay guard compares against `user_account.mfaLastStep`. Makes T008 pass.
- [ ] T030 Create `backend/src/main/java/com/caramelai/support/auth/RateLimiter.java`. In-memory fixed windows, with per-account keys stored as SHA-256 hashes. Sits behind an interface so a shared store can replace it later (research R6). Makes T011 pass.
- [ ] T031 Create `backend/src/main/java/com/caramelai/support/config/JwtConfig.java`. It provides a `JwtDecoder` and encoder for HS256 with `AUTH_JWT_SIGNING_KEY`, and fails startup when the key is missing or under 32 bytes. Create `backend/src/main/java/com/caramelai/support/auth/TokenService.java` to issue access tokens with claims `sub`, `sid`, `iat`, `exp`, `iss`, `aud`, and no role. Makes T012 pass.
- [ ] T032 Create `backend/src/main/java/com/caramelai/support/auth/RefreshTokenService.java`. It issues opaque 256-bit tokens (`SecureRandom`, base64url), stores only the SHA-256 hash, and rotates on each refresh, setting `rotated_at` on the old row. Makes T013 pass. Reuse handling is added with the grace-window tasks.
- [ ] T033 Create `backend/src/main/java/com/caramelai/support/auth/AuthenticatedUser.java`. The principal holds `userId`, `role`, `sessionId`, and `actorKind` (`USER`, `SHARED_TOKEN`).
- [ ] T034 Create `backend/src/main/java/com/caramelai/support/auth/ActiveSessionCheck.java`. Each authenticated request runs one indexed lookup that joins `sign_in_session` (by `sid`) with `user_account`. A request is rejected when `revokedAt` is set, `expiresAt` has passed, or the account is not `ACTIVE`. It returns the current role from the account row (research R2, plan C2). Target: adds under 5 ms at p95 (SC-008).
- [ ] T035 Create `backend/src/main/java/com/caramelai/support/config/SecurityConfig.java`. Settings: stateless sessions; OAuth2 resource server with the HS256 decoder from T031; `ActiveSessionCheck` runs on every bearer request. Public: `GET /api/auth/config`, `POST /api/auth/sign-up`, `POST /api/auth/email-verifications`, `POST /api/auth/password-reset-requests`, `POST /api/auth/set-password`, `POST /api/auth/sign-in`, `POST /api/auth/refresh`. Authenticated: `/api/agent/**`, `/api/admin/**`, `/api/me/**`, `/api/auth/me`, `/api/auth/sign-out-everywhere`. Chat endpoints stay anonymous-permitted. CSRF is disabled for bearer-only routes. Cookie routes check `Origin` and `X-Requested-With` (research R9). Makes T015 pass for these routes.
- [ ] T036 Update `backend/src/main/java/com/caramelai/support/config/AgentTokenFilter.java`. It becomes a second authenticator in the new chain. It produces an `AuthenticatedUser` with role `AGENT`, `actorKind = SHARED_TOKEN`, and no user ID. The constant-time comparison stays (research R8).
- [ ] T037 Create `backend/src/main/java/com/caramelai/support/api/AuthExceptionHandler.java`. It returns RFC 9457 problem responses with `type` values in the `urn:caramelai:problem:*` form and the request ID. Status codes 401, 403, 404, 409, and 429 use generic bodies that never name an account (contract `Problem`, `Unauthorized`, `NotFound`, `RateLimited`).
- [ ] T038 Create `backend/src/main/java/com/caramelai/support/audit/AuditEvent.java` and `audit/AuditService.java`. Rows are written through `Store`. Each write also emits one structured log line carrying the request ID and the user ID only. The event enum is the fixed vocabulary in data-model.md, and `detail` accepts only values from a fixed list. Makes the audit checks in T014 pass.
- [ ] T039 Update `backend/src/main/java/com/caramelai/support/config/RequestIdFilter.java`. The auth log line carries the request ID in MDC and the user ID as its only identifier. Keep JSON-safe keys, because duplicate keys break the log format (CLAUDE.md).
- [ ] T040 Update `backend/src/main/java/com/caramelai/support/config/CorsConfig.java` to make T016 pass. Settings: `allowCredentials(true)`; one origin from `app.cors.allowed-origin`; methods `GET, POST, PUT, PATCH, DELETE`; headers `Content-Type, Authorization, X-Requested-With, X-Request-Id, X-Agent-Token`; no wildcard (research R9).
- [ ] T041 Create `frontend/src/auth/accessTokenStore.ts`. The token is held in a module variable only (FR-006). Makes T017 pass.
- [ ] T042 Update `frontend/src/services/api.ts`, the only `fetch` module. Add `credentials: "include"`, the `Authorization` header when a token exists, and `X-Requested-With: caramel-chat` on cookie endpoints. It still surfaces only the problem title (CLAUDE.md). Makes T018 pass.

**Checkpoint**: Foundation is ready. Tests T007 to T018 pass. User-story work can start.

---

## Phase 3: User Story 2 - Admin manages agent accounts (Priority: P1) (Slice 1)

**Goal**: An admin creates agent and admin accounts, changes roles, and disables accounts. Disabling ends all sessions at once. This comes before US1 because agents cannot sign in until an admin creates their account (spec US2 is a prerequisite for US1).

**Independent Test**: As an admin, create an agent account, change its role, and disable it while it is signed in on two devices. Both sessions stop at once, and the account cannot sign in again (spec US2).

### Tests for User Story 2 (write first, must fail)

- [ ] T043 [P] [US2] Write `backend/src/test/java/com/caramelai/support/api/AdminAccountApiTest.java` (MockMvc). Cases: an admin creates an AGENT and gets 201 with one activation link; an AGENT gets 403; a CUSTOMER gets 403; an anonymous caller gets 401. This covers the access-matrix row "Manage agent accounts" (FR-029, FR-034).
- [ ] T044 [P] [US2] In the same test class, add the cases: disabling an account ends both sessions on the next request (401), and new sign-in is refused (FR-030, spec US2 AC2); a role change AGENT to ADMIN applies on the next request without re-issuing a token (FR-029, spec US2 AC3).
- [ ] T045 [P] [US2] Write `backend/src/test/java/com/caramelai/support/api/LastAdminGuardTest.java`. Cases: disabling the only active admin returns 409; demoting the only active admin returns 409; a second active admin makes the change allowed (FR-032, contract `409`).
- [ ] T046 [P] [US2] Write `backend/src/test/java/com/caramelai/support/integration/AccountAuditTest.java`. Each create, disable, and role change writes an `audit_event` with the admin's `actorUserId`, the subject, and the time (FR-031). The `detail` value for a role change is `role=ADMIN`, and the row contains no email (SC-010).
- [ ] T047 [P] [US2] Write `backend/src/test/java/com/caramelai/support/unit/OneTimeTokenServiceTest.java`. Cases: an ACTIVATION token is valid for 24 hours; issuing a new token consumes the older ones; a consumed token is refused; the token is stored only as a SHA-256 hash; the conditional update `WHERE consumed_at IS NULL AND expires_at > now` rejects a second use (data-model state transitions).

### Implementation for User Story 2

- [ ] T048 [BLOCKED: D12] Create `backend/src/main/java/com/caramelai/support/admin/BootstrapAdminRunner.java`. A startup `CommandLineRunner` creates one pending ADMIN when `AUTH_BOOTSTRAP_ADMIN_EMAIL` is set and no admin exists. It prints a one-time activation link to the console once and never writes it to a structured log (research R18). The owner must confirm the bootstrap approach first (plan D12).
- [ ] T049 [P] [US2] Create `backend/src/main/java/com/caramelai/support/auth/OneTimeTokenService.java`. It issues ACTIVATION and RESET tokens. Tokens are random, stored as SHA-256 hashes, and expire by purpose (activation 24 h, reset 30 min). A newer token of the same purpose consumes the older ones. Makes T047 pass.
- [ ] T050 [US2] Create `backend/src/main/java/com/caramelai/support/account/AccountService.java`, create path. Normalize the email (trim, lowercase). The new row has `role` AGENT or ADMIN, `status` ACTIVE, `passwordHash` null, `emailVerifiedAt` null, and `createdByUserId` set to the admin. Duplicate email returns 409. Writes `ACCOUNT_CREATED` (FR-031). Issues an activation link through T049 and returns it once in the response.
- [ ] T051 [US2] Extend `backend/src/main/java/com/caramelai/support/account/AccountService.java`, update path. Role and status changes run in one transaction with the last-admin guard, which counts active admins. Disabling calls the session revocation from T062 with reason `ACCOUNT_DISABLED`. Writes `ROLE_CHANGED` or `ACCOUNT_DISABLED` (FR-030, FR-031, FR-032). Makes T044 and T045 pass.
- [ ] T052 [US2] Create `backend/src/main/java/com/caramelai/support/admin/AdminAccountController.java`. Endpoints: `GET /api/admin/accounts` (filters `role`, `status`), `POST /api/admin/accounts`, `PATCH /api/admin/accounts/{accountId}`, and `POST /api/admin/accounts/{accountId}/activation-links` (409 when already enrolled). Each endpoint requires `ADMIN` (FR-034). The responses match the contract `Account`, `CreatedAccount`, and `ActivationLink`.
- [ ] T053 [US2] Make the audit tests pass for admin actions (T046) and the access-matrix checks for account management (T043) by wiring `AdminAccountController` to `AccountService` and `AuditService`.

**Checkpoint**: An admin can create, change, and disable agent accounts. Disabled accounts lose access on the next request. Tests T043 to T047 pass.

---

## Phase 4: User Story 1 - Agent signs in individually and every action is attributed (Priority: P1) (Slice 1)

**Goal**: An agent signs in with a password and an authenticator code, works on escalations assigned to them, and every view, status change, and reply is recorded against their account.

**Merge gate**: C1 must be recorded before this phase is merged. D3 must be confirmed before release.

**Independent Test**: Sign in as an agent account, open an assigned escalation, change its status, and send a reply. The audit records show the account and the time for each action (spec US1).

### Tests for User Story 1 (write first, must fail)

- [ ] T054 [P] [US1] Write `backend/src/test/java/com/caramelai/support/api/AuthSignInApiTest.java` (MockMvc). Case: an AGENT with the correct password and a correct TOTP code gets 200 with `accessToken`, `expiresInSeconds` 900, and a `Set-Cookie` of `caramel_refresh` that is `HttpOnly; Secure; SameSite=Lax; Path=/api/auth` (contract `signIn`, research R3).
- [ ] T055 [P] [US1] In the same class, add the cases: an AGENT with the correct password and no `totpCode` gets 401 `mfa-required`. A wrong password gets 401 `invalid-credentials`. An unknown email gets the same 401 body as a wrong password (research R6, contract `InvalidCredentials`).
- [ ] T056 [P] [US1] Write `backend/src/test/java/com/caramelai/support/api/AgentMfaTest.java`. Cases: a wrong TOTP code is refused and writes `MFA_FAILURE` (spec US1 AC3); a replayed code in the same step is refused (research R5); the first valid code after activation sets `mfaEnrolledAt` (research R5).
- [ ] T057 [P] [US1] Write `backend/src/test/java/com/caramelai/support/api/ActivationApiTest.java`. Case: `POST /api/auth/set-password` with a valid ACTIVATION token sets `passwordHash` and `emailVerifiedAt`; the token cannot be reused; a token past 24 hours is refused. The enrollment URI is asserted in T058 (research R5).
- [ ] T058 [P] [US1] Write the failing test for activation enrollment: the activation response includes the `otpauth://` URI once, as research R5 requires. This depends on the contract change in T086. Mark the test as a contract-change test until T086 is merged.
- [ ] T059 [P] [US1] Write `backend/src/test/java/com/caramelai/support/api/AccessMatrixAgentTest.java`. Cases: anonymous gets 401 on `GET /api/agent/escalations`; a CUSTOMER gets 403; an AGENT gets 200; an ADMIN gets 200 and sees all escalations; the shared token gets 200 and sees all escalations (spec US1 AC4, FR-024, FR-026).
- [ ] T060 [P] [US1] [BLOCKED: D6] Write `backend/src/test/java/com/caramelai/support/api/AgentEscalationScopeTest.java`. Cases: an agent sees only escalations whose `assignedAgentId` is the agent; a foreign or unassigned escalation returns 404 with the same body as a missing one (FR-025, Constitution VI). Mark the assignment fixture as blocked by D6.
- [ ] T061 [P] [US1] [BLOCKED: D6] Write `backend/src/test/java/com/caramelai/support/api/AgentActionAuditTest.java`. Cases: viewing an escalation writes `ESCALATION_VIEWED`, a status change writes `ESCALATION_STATUS_CHANGED`, and a reply writes `ESCALATION_REPLIED`. Each row holds the agent's `actorUserId` and the time (FR-027, SC-003). The assigned fixture is blocked by D6.
- [ ] T062 [P] [US1] Write `backend/src/test/java/com/caramelai/support/unit/RefreshReuseTest.java`. Case: a rotated refresh token presented after the grace window ends every session of the account, with reason `TOKEN_REUSE`, and writes `TOKEN_REUSE_DETECTED` (spec Edge Cases, research R3). The grace window itself is tested in T063.
- [ ] T063 [P] [BLOCKED: C5] [US1] Write the grace-window cases in `RefreshReuseTest`. A rotated token presented within the window is refused with 401 and revokes nothing. The window length is 10 seconds by default. The owner must confirm C5 before this test is final.
- [ ] T064 [P] [US1] Write `backend/src/test/java/com/caramelai/support/api/SignOutApiTest.java`. Cases: sign-out ends the session on the server, so its bearer token and its refresh token both fail (FR-007); sign-out-everywhere ends every session of the account, including the current one (FR-008, contract `signOutEverywhere`).
- [ ] T065 [P] [US1] Write `backend/src/test/java/com/caramelai/support/api/AuthRateLimitTest.java`. Cases: the 21st sign-in from one IP within a minute gets 429 with a generic body and `Retry-After` (FR-013, contract `RateLimited`).
- [ ] T066 [P] [US1] Write `backend/src/test/java/com/caramelai/support/api/AgentLockoutTest.java`. Cases: five consecutive wrong passwords lock the account for 15 minutes; the sixth attempt gets the same 401 body as a wrong password; `SIGN_IN_LOCKED` is audited with the account ID and no email (FR-012, research R6).
- [ ] T067 [P] [US1] Write `backend/src/test/java/com/caramelai/support/api/SharedTokenPhaseOneTest.java`. Cases: `X-Agent-Token` still works in Phase 1; its actions are audited with `actorKind = SHARED_TOKEN` and no `actorUserId` (research R8, contract `agentToken`).
- [ ] T068 [P] [US1] [BLOCKED: D6] Write `backend/src/test/java/com/caramelai/support/api/DisabledAgentMidCaseTest.java`. Case: an agent disabled while handling an escalation has the next action refused, and the escalation stays available to other agents (spec Edge Cases). Mark the assignment fixture as blocked by D6.
- [ ] T069 [P] [US1] Write `frontend/tests/auth/SignInForm.test.tsx`. Cases: when the server answers `mfa-required`, a TOTP field appears; the error text never says whether the email is registered (FR-039); the form is usable by keyboard with labelled fields.
- [ ] T070 [P] [US1] Write `frontend/tests/auth/AuthProvider.test.tsx`. Cases: on load, one silent `POST /api/auth/refresh` restores the session; a 401 triggers one refresh and one retry; no token is written to storage (FR-006).
- [ ] T071 [P] [US1] Write `frontend/tests/auth/SetPasswordForm.test.tsx` for activation. Cases: a password under 12 characters is blocked on the client and the server rule is shown; the enrollment URI appears once and is not stored.

### Implementation for User Story 1

- [ ] T072 [US1] Create `backend/src/main/java/com/caramelai/support/auth/LockoutPolicy.java` to make T010 pass. Persists `failedSignInCount`, `nextAttemptAt`, and `lockedUntil` on `user_account` (research R6).
- [ ] T073 [US1] Create `backend/src/main/java/com/caramelai/support/auth/SignInService.java`. Steps in order: check the lockout and delay; run the bcrypt check, using the dummy hash for an unknown email; if the password is correct and the role is AGENT or ADMIN, require and check the TOTP code (`mfa-required` only after the password is correct). On failure, record it through `LockoutPolicy` and return the generic 401. On success, create a `sign_in_session`, issue a refresh token and access token, and write `SIGN_IN_SUCCESS`. Makes T054, T055, and T066 pass.
- [ ] T074 [US1] Add the TOTP enrollment path to `SignInService`. The first valid code for an account with `mfaEnrolledAt` null sets `mfaEnrolledAt` and `mfaLastStep` (research R5). Makes T056 pass.
- [ ] T075 [US1] Create `backend/src/main/java/com/caramelai/support/auth/SessionService.java` with two methods. `signOut` ends the current session with reason `SIGN_OUT` and writes `SESSION_ENDED`. `signOutEverywhere` ends every session of the account with reason `SIGN_OUT_ALL` and writes `SESSIONS_ENDED_ALL`. Makes T064 pass.
- [ ] T076 [US1] Extend `backend/src/main/java/com/caramelai/support/auth/RefreshTokenService.java` with reuse handling. A rotated token presented after the grace window revokes every session of the account (reason `TOKEN_REUSE`) and writes `TOKEN_REUSE_DETECTED`. Makes T062 pass.
- [ ] T077 [US1] [BLOCKED: C5] Add the grace-window behavior to `RefreshTokenService`, with the window value from `AuthProperties`. A rotated token inside the window gets 401 and revokes nothing. Makes T063 pass.
- [ ] T078 [US1] Create `backend/src/main/java/com/caramelai/support/auth/AuthController.java` with `POST /api/auth/sign-in`, `POST /api/auth/refresh` (checks `Origin` and `X-Requested-With`, research R9), `POST /api/auth/sign-out`, `POST /api/auth/sign-out-everywhere`, `GET /api/auth/me`, and `POST /api/auth/set-password` for ACTIVATION. The responses match the contract schemas.
- [ ] T079 [US1] Wire sign-in rate limiting in `AuthController` at 20 per minute per IP through `RateLimiter` (research R6). Makes T065 pass.
- [ ] T080 [US1] Implement activation in `AuthController` `set-password`. Consume the ACTIVATION token with a conditional update, set `passwordHash` and `emailVerifiedAt`, and generate the TOTP secret, stored through `SecretCipher`. Makes T057 pass.
- [ ] T081 [US1] Extend `AuthController` `POST /api/auth/refresh` to return the new access token and rotate the cookie with the same attributes as in T054.
- [ ] T082 [US1] Create `backend/src/main/java/com/caramelai/support/agent/EscalationWorkflowService.java`. It handles view, status change, and reply. The first view sets `firstOpenedAt` and moves status NEW to ACKNOWLEDGED. Each action writes its audit event with the actor (FR-027). Makes T061 pass once the fixtures exist.
- [ ] T083 [US1] [BLOCKED: D6] Create `backend/src/main/java/com/caramelai/support/admin/AdminEscalationController.java` with `PUT /api/admin/escalations/{escalationId}/assignment`. An admin assigns or reassigns an escalation; null unassigns; the target must be an active AGENT or ADMIN. Writes `ESCALATION_ASSIGNED` (plan D6, contract `assignEscalation`).
- [ ] T084 [US1] [BLOCKED: D6] Update `backend/src/main/java/com/caramelai/support/api/AgentEscalationController.java`. The list returns only escalations with `assignedAgentId = :me` for agents. Admins see all. The shared token sees all, audited without an actor (research R8). Foreign or unassigned items return 404. Makes T059 and T060 pass.
- [ ] T085 [US1] [BLOCKED: D7] Add `POST /api/agent/escalations/{escalationId}/status` to `AgentEscalationController`. Values are `NEW`, `ACKNOWLEDGED`, and `CLOSED`. Only an admin can reopen a `CLOSED` escalation (research R12). Makes the status cases of T061 pass.
- [ ] T086 [US1] Update `specs/002-user-auth/contracts/auth-api.yaml`. `POST /api/auth/set-password` returns `204` only, but research R5 says the `otpauth://` enrollment URI is returned once at activation. Add a `200` response that carries the URI, and make the T058 test pass against it. This is a contract fix, not a behavior change.
- [ ] T087 [US1] [BLOCKED: D6] Add `POST /api/agent/escalations/{escalationId}/replies` to `AgentEscalationController`. Replies are stored in `escalation_reply` with `authorKind` and the author's ID, trimmed, 1 to 2,000 characters (research R12). Replies never enter the chat prompt. This task is blocked by D6 because replies require assignment. Delivery to customers is not built (D8).
- [ ] T088 [US1] Create the frontend `frontend/src/auth/AuthProvider.tsx`. It holds the auth context, restores the session with one silent refresh on load, refreshes shortly before expiry, and retries once on 401 (research R15). Makes T070 pass.
- [ ] T089 [US1] Create `frontend/src/components/SignInForm.tsx`. Fields are email, password, and a TOTP field shown on `mfa-required`. Errors are generic. Makes T069 pass.
- [ ] T090 [US1] Create `frontend/src/components/SetPasswordForm.tsx` for activation. It enforces the password rules on the client and shows the enrollment URI once (research R5). Makes T071 pass.
- [ ] T091 [US1] [BLOCKED: D6] [BLOCKED: D7] Create `frontend/src/components/AgentQueue.tsx` and `frontend/src/components/EscalationDetail.tsx`. Show only the escalations the server returns, and the status and reply actions. Blocked by D6 for the queue contents and by D7 for status controls.

**Checkpoint**: Agents sign in with a password and TOTP. Each action is attributed. Shared-token actions are audited without an actor. Tests T054 to T071 pass, with blocked tests noted.

---

## Phase 5: User Story 3 - Customer signs up, verifies email, and signs in and out (Priority: P2) (Slice 2)

**Goal**: A customer creates an account, verifies the email, and signs in and out. Customer sign-in is behind `AUTH_CUSTOMER_SIGNIN_ENABLED`.

**Merge gate**: D10 must be confirmed before Slice 2 launch. D4 must be confirmed before a real email provider is used.

**Independent Test**: Sign up with a new address, verify it through the emailed link, sign in, sign out, and sign in again (spec US3).

### Tests for User Story 3 (write first, must fail)

- [ ] T092 [P] [US3] Write `backend/src/test/java/com/caramelai/support/api/SignUpApiTest.java`. Cases: a new address and a registered address both get 202 with the same body (FR-011, SC-009); a registered address creates no account and writes `SIGN_UP_DUPLICATE`; a password under 12 characters or on the common-password list gets 400 with the rule named and no account hint (FR-003, spec US3 AC3).
- [ ] T093 [P] [US3] Write `backend/src/test/java/com/caramelai/support/api/EmailVerificationApiTest.java`. Cases: a link verifies the account once; a second use is refused; a link older than 24 hours is refused; a superseded link is refused (contract `verifyEmail`, data-model `EmailVerificationToken`).
- [ ] T094 [P] [US3] Write `backend/src/test/java/com/caramelai/support/api/UnverifiedChatTest.java`. Cases: a signed-in unverified customer's new session has no `userId` (FR-002, spec US3 AC2); after verification, a new session has `userId` set.
- [ ] T095 [P] [US3] Write `backend/src/test/java/com/caramelai/support/api/CustomerSignInTest.java`. Cases: a customer signs in with email and password and gets 200 with no TOTP requirement (customer MFA is out of scope); a wrong password and an unknown email get the same 401 body (spec US3 AC5); sign-out makes the session and refresh token fail (FR-007, spec US3 AC4).
- [ ] T096 [P] [US3] Write `backend/src/test/java/com/caramelai/support/api/FeatureFlagTest.java`. Cases: with `AUTH_CUSTOMER_SIGNIN_ENABLED=false`, sign-up, customer sign-in, and customer endpoints return 404; `GET /api/auth/config` returns `customerSignInEnabled: false`; anonymous chat behaves as in v1 (FR-037, spec Edge Cases).
- [ ] T097 [P] [US3] Write `backend/src/test/java/com/caramelai/support/unit/AsyncEmailSendTest.java`. Cases: sign-up returns before the email is sent; a send failure writes `EMAIL_SEND_FAILED` with no address; the response does not depend on the send outcome (research R10, FR-011).
- [ ] T098 [P] [US3] Write `backend/src/test/java/com/caramelai/support/unit/EmailMessagesTest.java`. Cases: a verification message contains a single-use link with the token in the fragment (`#token=`) and contains no password or session credential (FR-038, research R10).
- [ ] T099 [P] [US3] Write `frontend/tests/auth/SignUpForm.test.tsx`. Cases: the form shows the password rule before submit; the success screen is the same for new and registered addresses; the page is keyboard-operable with labelled fields (FR-039).

### Implementation for User Story 3

- [ ] T100 [US3] Create `backend/src/main/java/com/caramelai/support/email/EmailSender.java` (interface with one call, `send(template, recipientRef, link)`), `email/OutboxEmailSender.java` (writes to `backend/data/outbox/` under the `dev` profile and in tests), and `email/EmailMessages.java` (fixed templates, link only, token in the fragment). Makes T098 pass.
- [ ] T101 [US3] Create `backend/src/main/java/com/caramelai/support/email/AsyncEmailSender.java`. Sends run on an executor after the response is returned, and failures write `EMAIL_SEND_FAILED` with no address. Makes T097 pass.
- [ ] T102 [US3] [BLOCKED: D4] Create the production email provider adapter behind `EmailSender`. The production profile fails at startup when `EMAIL_PROVIDER_API_KEY` is missing once Phase 2 is enabled (research R10). The provider is not chosen.
- [ ] T103 [US3] Create `backend/src/main/java/com/caramelai/support/account/CustomerSignUpService.java` (or add the method to `AccountService`). New address: create a `CUSTOMER` with `passwordHash` set, `emailVerifiedAt` null, and `ACCOUNT_CREATED`; send the verification link asynchronously. Registered address: write `SIGN_UP_DUPLICATE` and send the owner notice asynchronously. Both return the same 202 body. Enforce `PasswordPolicy` before any lookup (FR-003). Makes T092 pass.
- [ ] T104 [US3] Extend `backend/src/main/java/com/caramelai/support/auth/OneTimeTokenService.java` for email verification. Verification tokens expire 24 hours after sending, are single-use, and are superseded by a newer link (spec Assumptions, data-model `EmailVerificationToken`). Makes T093 pass.
- [ ] T105 [US3] Add `POST /api/auth/sign-up`, `POST /api/auth/email-verifications`, and `POST /api/auth/email-verifications/resend` to `AuthController`. The resend endpoint needs a bearer token of an unverified customer. Sign-up is rate limited at 10 per hour per IP and 3 per hour per target email, silently (research R6). Makes T092 and T093 pass.
- [ ] T106 [US3] Create `backend/src/main/java/com/caramelai/support/auth/AuthConfigController.java` with `GET /api/auth/config`. It returns `customerSignInEnabled` from `AUTH_CUSTOMER_SIGNIN_ENABLED`.
- [ ] T107 [US3] Add the feature-flag gate in `backend/src/main/java/com/caramelai/support/config/CustomerFeatureFilter.java`. When the flag is off, customer sign-up, customer sign-in, and `/api/me/**` return 404. Agent and admin endpoints are not affected (contract, root description). Makes T096 pass.
- [ ] T108 [US3] Add customer sign-in to `SignInService`. A customer skips the TOTP step. Lockout and the identical 401 body apply as for agents (research R6). Makes T095 pass.
- [ ] T109 [US3] Create `frontend/src/components/SignUpForm.tsx`, `frontend/src/components/VerificationNotice.tsx`, and the customer path of `SignInForm.tsx`. Makes T099 pass.

**Checkpoint**: A customer signs up, verifies the email, and signs in and out, with the flag on. With the flag off, customer endpoints return 404. Tests T092 to T099 pass.

---

## Phase 6: User Story 4 - Customer resets a forgotten password and manages sessions (Priority: P2) (Slice 2)

**Goal**: A customer requests a reset by email, sets a new password through a single-use link, and can end all sessions at once.

**Independent Test**: Request a reset, use the link to set a new password, and confirm that the old session stops working. Separately, sign in on three devices and use "sign out everywhere" on one (spec US4).

### Tests for User Story 4 (write first, must fail)

- [ ] T110 [P] [US4] Write `backend/src/test/java/com/caramelai/support/api/PasswordResetApiTest.java`. Case: a reset request for a registered and an unknown address gets the same 202 body (FR-011, SC-009, spec US4 AC1). Per-target limit of 3 per hour is silent.
- [ ] T111 [P] [US4] In the same class, add the cases: a valid link sent under 30 minutes ago sets the new password, and every session that existed before the reset stops working (FR-010, spec US4 AC2); the link is single-use; a link older than 30 minutes is refused with a re-request offer (spec US4 AC3); `RESET_REQUESTED` and `RESET_COMPLETED` are audited with no email.
- [ ] T112 [P] [US4] Add to the same class: a second reset request supersedes the first, so the first link is refused (FR-009, spec Edge Cases).
- [ ] T113 [P] [US4] Write `backend/src/test/java/com/caramelai/support/api/SignOutEverywhereCustomerTest.java`. Case: a customer signed in on three devices calls sign-out-everywhere from one, and all three stop (FR-008, spec US4 AC4).
- [ ] T114 [P] [US4] Write `frontend/tests/auth/ResetForms.test.tsx`. Cases: the reset request screen shows the same confirmation for any address; the set-password screen rejects a password under 12 characters on the client; an expired link shows the re-request offer.

### Implementation for User Story 4

- [ ] T115 [US4] Create `backend/src/main/java/com/caramelai/support/auth/PasswordResetService.java`. Request: issue a RESET token with `OneTimeTokenService`, consuming any earlier one, and send asynchronously. Complete: consume the token conditionally, set the new password, end every session with reason `PASSWORD_RESET`, and write `RESET_COMPLETED`. Makes T111 and T112 pass.
- [ ] T116 [US4] Add `POST /api/auth/password-reset-requests` to `AuthController`, with the same 202 for every address and the per-target and per-IP limits from research R6. Makes T110 pass.
- [ ] T117 [US4] Extend `AuthController` `POST /api/auth/set-password` to handle the RESET purpose as well as ACTIVATION.
- [ ] T118 [US4] Add the sign-out-everywhere control to `frontend/src/components/AccountSettings.tsx` (created in Phase 10, a stub here). Makes T113 pass on the server path.
- [ ] T119 [US4] [BLOCKED: D14] Define the customer-visible message for an email outage on sign-up, verification, and reset. Research R10 and plan C4 keep identical responses and show a retry path, so the text is not final until the owner confirms D14. Identical responses and async sends are already covered in T092 and T110.
- [ ] T120 [US4] Create `frontend/src/components/ResetRequestForm.tsx` and the reset mode of `SetPasswordForm.tsx`. Makes T114 pass.

**Checkpoint**: Reset works end to end. Old sessions stop after a reset. A customer can sign out everywhere. Tests T110 to T114 pass.

---

## Phase 7: User Story 5 - Signed-in customer sees and reopens their own conversations (Priority: P2) (Slice 2)

**Goal**: A signed-in, verified customer sees their own conversations, newest first, and can reopen and continue one. Everything else gets the same 404 as a missing item.

**Independent Test**: Sign in as customer A, have a conversation, sign out, sign in as customer B, and confirm B cannot see or open A's conversation (spec US5).

### Tests for User Story 5 (write first, must fail)

- [ ] T121 [P] [US5] Write `backend/src/test/java/com/caramelai/support/api/CustomerHistoryApiTest.java`. Cases: `GET /api/me/sessions` lists only the caller's sessions, newest first, with `limit` between 1 and 100 (contract `listMySessions`); a missing or foreign `sessionId` gets the same 404 (FR-017, SC-004, spec US5 AC3).
- [ ] T122 [P] [US5] Write `backend/src/test/java/com/caramelai/support/api/SessionOwnershipTest.java`. Cases: a message, rating, or escalation against an owned session by another user gets 404 identical to a missing session; an anonymous session keeps the v1 rule (research R7).
- [ ] T123 [P] [US5] In the same class, add: `POST /api/sessions` with a valid bearer of a verified customer and the flag on creates an owned session; an invalid or expired bearer gets 401 and is never treated as anonymous (contract `createSession`).
- [ ] T124 [P] [US5] Write `backend/src/test/java/com/caramelai/support/api/AnonymousHistoryTest.java`. Case: `GET /api/me/sessions` with no credentials gets 401, and the chat UI offers no history list (spec US5 AC4).

### Implementation for User Story 5

- [ ] T125 [US5] Update `backend/src/main/java/com/caramelai/support/persistence/ChatSession.java` to add `userId` and `claimedAt`. `userRef` is unchanged and is never used for identity (research R17).
- [ ] T126 [US5] Create `backend/src/main/java/com/caramelai/support/chat/SessionOwnership.java`. It checks that a session with a non-null `userId` matches the caller, and throws `SessionNotFoundException` (404) on a mismatch. A null `userId` keeps the v1 rule.
- [ ] T127 [US5] Update `backend/src/main/java/com/caramelai/support/api/SessionController.java`. Owner is set on create only for a verified customer with the flag on (research R7). Makes T123 pass.
- [ ] T128 [US5] Update `backend/src/main/java/com/caramelai/support/api/MessageController.java` and `api/RatingController.java` to call `SessionOwnership`. Makes T122 pass for these routes.
- [ ] T129 [US5] Create `backend/src/main/java/com/caramelai/support/api/CustomerHistoryController.java` with `GET /api/me/sessions` and `GET /api/me/sessions/{sessionId}`. The responses match contract `SessionSummary` and `SessionDetail`. Makes T121 and T124 pass.
- [ ] T130 [US5] Create `frontend/src/components/HistoryList.tsx`. It shows the list when signed in and nothing when anonymous.
- [ ] T131 [US5] Update `frontend/src/hooks/useChat.ts` for optional identity and for reopening and continuing a session. Anonymous chat behaves as in v1.

**Checkpoint**: A customer sees and reopens only their own sessions. Tests T121 to T124 pass.

---

## Phase 8: User Story 7 - Escalations from signed-in customers carry verified identity (Priority: P2) (Slice 2)

**Goal**: An escalation from a signed-in, verified customer carries their account ID and email. The agent sees both. An anonymous escalation carries no identity.

**Independent Test**: Sign in as a customer, ask for a human, and confirm the agent's view shows the verified email and account ID. Repeat anonymously and confirm there is no account identity (spec US7).

### Tests for User Story 7 (write first, must fail)

- [ ] T132 [P] [US7] Write `backend/src/test/java/com/caramelai/support/api/EscalationIdentityTest.java`. Cases: a signed-in verified customer's escalation stores `customerUserId` and `customerEmail` and the transcript and time (FR-019, spec US7 AC1); an anonymous escalation stores neither (FR-019, spec US7 AC3).
- [ ] T133 [P] [US7] In the same class, add: the agent detail includes `customer.userId` and `customer.email` for account-linked escalations (FR-028, spec US7 AC2); the summary has no `customer` field for anonymous escalations, as in v1 (contract `AgentEscalationSummary`).
- [ ] T134 [P] [US7] Extend `backend/src/test/java/com/caramelai/support/api/EscalationApiTest.java` with the token-to-account cases that plan Supersession lists for Slice 1. Anonymous behavior must not change.
- [ ] T135 [P] [US7] Write `frontend/tests/auth/EscalationDetailIdentity.test.tsx`. Case: the detail view shows the verified email and account ID, and anonymous escalations show no identity block.

### Implementation for User Story 7

- [ ] T136 [US7] Update `backend/src/main/java/com/caramelai/support/escalation/EscalationService.java`. When the session has a verified owner, snapshot `customerUserId` and `customerEmail` on the escalation (FR-019). Makes T132 pass.
- [ ] T137 [US7] Update `backend/src/main/java/com/caramelai/support/api/EscalationController.java` so identity is attached when the request is signed in. Anonymous requests are unchanged.
- [ ] T138 [US7] Update `AgentEscalationSummary` and `AgentEscalationDetail` in `backend/src/main/java/com/caramelai/support/api/AgentEscalationController.java` to include the optional `customer` block (FR-028). Makes T133 pass.
- [ ] T139 [US7] Update `frontend/src/components/EscalationDetail.tsx` to show the verified email and account ID. Makes T135 pass.

**Checkpoint**: Escalations from signed-in customers carry verified identity. Anonymous escalations are unchanged. Tests T132 to T135 pass.

---

## Phase 9: User Story 6 - Anonymous conversation is attached to the account at sign-in (Priority: P3) (Slice 2)

**Goal**: A visitor who chatted anonymously signs in during the same session, and the conversation is attached to their account. A conversation can be attached only once.

**Independent Test**: Start an anonymous chat, sign in, and confirm the conversation appears in the account's history. Then try to attach the same conversation to a second account and confirm it is refused (spec US6).

### Tests for User Story 6 (write first, must fail)

- [ ] T140 [P] [US6] Write `backend/src/test/java/com/caramelai/support/api/ClaimApiTest.java`. Cases: sign-in with `claimSessionId` of an unowned anonymous session returns `claim: {requested: true, claimed: true, reason: claimed}` and the session appears in history (spec US6 AC1); a second claim of the same session by the same holder is refused (spec US6 AC3).
- [ ] T141 [P] [BLOCKED: D13] [US6] Write `backend/src/test/java/com/caramelai/support/api/ClaimEmailVerifiedTest.java`. Cases: a claim from an unverified customer is refused with `reason: email_not_verified`, and the session stays claimable later (FR-002, research R7). Blocked by D13.
- [ ] T142 [P] [US6] In `ClaimApiTest`, add: a session already claimed by another account returns `claimed: false` with `reason: not_found`, the same as for a nonexistent session (spec US6 AC2, research R7). Note: quickstart step 5 currently expects `already_claimed`, which discloses existence. Fix the quickstart in T147.
- [ ] T143 [P] [US6] In `ClaimApiTest`, add a concurrency case: two concurrent claims of one session, only one succeeds (research R7, conditional `UPDATE … WHERE user_id IS NULL`).
- [ ] T144 [P] [US6] Add a case that a new chat started before sign-in is not attached: sign-in without `claimSessionId` leaves earlier sessions unowned (spec US6 AC4).

### Implementation for User Story 6

- [ ] T145 [US6] Create `backend/src/main/java/com/caramelai/support/auth/ClaimService.java`. Conditions: the password and any TOTP check have passed; the account is a verified customer; the session exists; `userId` is null; no earlier claim. One conditional update sets `userId` and `claimedAt`. Makes T140, T142, T143, and T144 pass.
- [ ] T146 [US6] [BLOCKED: D13] Add the email-verified gate to `ClaimService`. Refuse with `email_not_verified` for an unverified account (research R7). Makes T141 pass.
- [ ] T147 [US6] Update `specs/002-user-auth/quickstart.md` step 5. The second claim must expect `claimed: false` and `reason: not_found`, not `already_claimed`, so the response does not reveal that another account owns the session (spec US6 AC2).
- [ ] T148 [US6] Update `frontend/src/hooks/useChat.ts` to send `claimSessionId` with sign-in when the current session is anonymous and held by the visitor.

**Checkpoint**: Anonymous conversations attach once, at sign-in. Foreign and missing sessions look the same. Tests T140 to T144 pass; T141 waits on D13.

---

## Phase 10: User Story 8 - Customer deletes chat history or account (Priority: P3) (Slice 2)

**Goal**: A signed-in customer deletes their history, or their account, with the password. Deletion removes sessions, messages, and ratings. Escalations follow the rule in research R13.

**Independent Test**: Delete history and confirm the conversations are gone. Delete the account and confirm sign-in is refused and the conversations are gone (spec US8).

### Tests for User Story 8 (write first, must fail)

- [ ] T149 [P] [US8] Write `backend/src/test/java/com/caramelai/support/api/DeleteHistoryTest.java`. Cases: `DELETE /api/me/history` removes sessions, messages, and ratings, and none appear in any list (FR-020, spec US8 AC1); the account remains and can still sign in.
- [ ] T150 [P] [US8] Write `backend/src/test/java/com/caramelai/support/api/DeleteAccountTest.java`. Cases: `DELETE /api/me` with the wrong password is refused; with the right password it ends every session, refuses sign-in, and removes the conversations, messages, and ratings (FR-021, spec US8 AC2, contract `deleteAccount`).
- [ ] T151 [P] [US8] Write the audit and log checks in `DeleteAccountTest`. Audit rows keep the user ID only, for the retention period (research R13, R14). No email appears in any audit row or log line (SC-010).
- [ ] T152 [P] [BLOCKED: D9] [BLOCKED: C6] [US8] Write `backend/src/test/java/com/caramelai/support/api/EscalationOnDeletionTest.java`. Cases: an unopened escalation (`firstOpenedAt` null) is deleted with the history; an opened escalation keeps the transcript with `sessionId`, `customerUserId`, and `customerEmail` cleared (research R13). The spec says "already received", not "opened". Blocked until the owner confirms the threshold.
- [ ] T153 [P] [US8] Write `frontend/tests/auth/AccountSettings.test.tsx`. Cases: delete history and delete account each ask for confirmation; delete account requires the password field; the success state signs the user out.

### Implementation for User Story 8

- [ ] T154 [US8] Create `backend/src/main/java/com/caramelai/support/account/AccountDeletionService.java`. For history: delete the customer's sessions, messages, and ratings explicitly, because `ON DELETE SET NULL` alone would keep rows (data-model schema notes). For account: revoke every session with reason `ACCOUNT_DELETED`, run the history deletion, and delete the account row. Makes T149 and T150 pass.
- [ ] T155 [US8] [BLOCKED: D9] [BLOCKED: C6] Add the escalation rule from research R13 to `AccountDeletionService`. Makes T152 pass after the owner confirms it.
- [ ] T156 [US8] Add `DELETE /api/me/history` to `CustomerHistoryController` and `DELETE /api/me` to a new `backend/src/main/java/com/caramelai/support/api/AccountController.java`. Deletion of the account requires the password (contract `PasswordConfirmation`).
- [ ] T157 [US8] Create `frontend/src/components/AccountSettings.tsx`. Actions: sign out, sign out everywhere (T118), delete history, delete account. Makes T153 pass.

**Checkpoint**: Deletion works as specified, except the escalation rule, which waits on D9 and C6.

---

## Phase 11: Migration cleanup - Slice 3 (FR-033, SC-011)

**Purpose**: Remove the shared agent token after the migration window. The window end date is set by the owner.

**Blocked**: [BLOCKED: D11]. Do not start this phase until the owner sets the migration window end date.

- [ ] T158 Write `backend/src/test/java/com/caramelai/support/api/SharedTokenRemovedTest.java` first. Cases: `X-Agent-Token` returns 401 or 404 on every route that accepted it (SC-011, contract `agentToken`); `AGENT_API_TOKEN` is not read from configuration.
- [ ] T159 [BLOCKED: D11] Remove `backend/src/main/java/com/caramelai/support/config/AgentTokenFilter.java` and its registration in `SecurityConfig`.
- [ ] T160 [BLOCKED: D11] Remove `AGENT_API_TOKEN` from `.env.example`, `backend/src/main/resources/application.yml`, and the docs.
- [ ] T161 [BLOCKED: D11] Remove `X-Agent-Token` from the allowed headers in `CorsConfig`.
- [ ] T162 [BLOCKED: D11] Remove the token cases from `backend/src/test/java/com/caramelai/support/api/EscalationApiTest.java` (plan Supersession).
- [ ] T163 [BLOCKED: D11] Remove the `agentToken` security scheme and its references from `specs/002-user-auth/contracts/auth-api.yaml`.
- [ ] T164 [BLOCKED: D11] Confirm SC-003: after the window, the audit query for agent actions with `actorKind = SHARED_TOKEN` returns zero rows (data-model, derived checks).
- [ ] T165 Mark the superseded v1 artifacts listed in plan Supersession, which the constitution requires to be updated per slice. Update `specs/001-support-chat-assistant/spec.md` (FR-017 and the personalization out-of-scope line), `specs/001-support-chat-assistant/plan.md` (constitution-check row), `specs/001-support-chat-assistant/data-model.md` (`userRef`, research R17), and `specs/001-support-chat-assistant/contracts/chat-api.yaml` (agent endpoints). Mark each as superseded by spec 002. Do not delete v1 content.

---

## Phase 12: Polish and cross-cutting checks

**Purpose**: Access-matrix coverage, performance, retention, and release checks.

- [ ] T166 Write `backend/src/test/java/com/caramelai/support/api/AccessMatrixTest.java`. One test per row of the spec's Access Matrix (anonymous, customer, agent, admin), with a cross-user case for every owned resource (Constitution IV, SC-005).
- [ ] T167 [P] Write `backend/src/test/java/com/caramelai/support/integration/AuthOutageChatTest.java`. With the account repository replaced by a throwing test double, anonymous sessions and messages still return v1 responses (FR-014, SC-006).
- [ ] T168 [P] Write `backend/src/test/java/com/caramelai/support/audit/AuditRetentionJobTest.java`. Rows older than 90 days are deleted; newer rows are kept (FR-036, research R14).
- [ ] T169 Create `backend/src/main/java/com/caramelai/support/audit/AuditRetentionJob.java`. It runs daily and deletes `audit_event` rows older than 90 days. Makes T168 pass. Required before Slice 2 launch (D10).
- [ ] T170 [P] Create `backend/src/test/java/com/caramelai/support/perf/AuthPerformanceTest.java`, tagged so it runs on demand. Measure sign-in p95 under 500 ms (50 sequential, 10 concurrent) and authenticated overhead p95 under 5 ms against an anonymous GET (SC-007, SC-008, quickstart Performance). Set the bcrypt cost from measurement.
- [ ] T171 [P] Document the 90-day audit retention and the email-storage rule in `docs/`, as FR-036 requires.
- [ ] T172 [BLOCKED: C1] Record owner approval of constitution v2.0.0 (plan C1) and remove the SYNC IMPACT HTML comment from `.specify/memory/constitution.md` before commit (the comment says so).
- [ ] T173 Run `cd backend && mvn test` and `cd frontend && npm test && npm run build`. The bundle-secret test (T004) must pass on `dist/`.
- [ ] T174 Run every slice's verification steps in `specs/002-user-auth/quickstart.md` against a dev environment, and record the results in the PR.
- [ ] T175 [P] Update `CLAUDE.md` only where its auth description no longer matches the code. Do not change constitution rules there.

---

## Dependencies and execution order

- **Phase 1 to Phase 2**: Phase 2 starts after Phase 1. T005 waits on DB.
- **Phase 2 blocks everything**: No user-story phase starts before Phase 2 passes.
- **Phase 3 (US2) before Phase 4 (US1)**: Agents need accounts first.
- **Phase 4 (US1)** can merge only after C1. Its assignment and status tasks wait on D6 and D7.
- **Phase 5 (US3)** starts after Phase 2. It is the first step of Slice 2 and is behind the flag.
- **Phase 6 (US4)** and **Phase 7 (US5)** depend on Phase 5 for accounts and sign-in.
- **Phase 8 (US7)** depends on Phase 7 (ownership) and on Phase 4 (agent view).
- **Phase 9 (US6)** depends on Phase 5 (sign-in) and Phase 7 (ownership).
- **Phase 10 (US8)** depends on Phase 7 and Phase 9.
- **Phase 11 (Slice 3)** waits on D11 and on Slice 2 being live.
- **Phase 12 (polish)** runs last, except T170 and T171, which can run any time after Phase 2.

### Parallel opportunities

- Phase 2 tests T007 to T018 touch different files and can all run in parallel.
- Phase 2 schema tasks T022 and T023 can run in parallel with each other, after T021.
- Within each story, test tasks marked [P] can run in parallel.
- Frontend tests (T017, T018, T069, T070, T071, T099, T114, T135, T153) are independent of backend tests.

### Example: User Story 1 test batch

```text
T054 AuthSignInApiTest.java
T056 AgentMfaTest.java
T059 AccessMatrixAgentTest.java
T064 SignOutApiTest.java
T069 SignInForm.test.tsx
```

## Implementation strategy

### MVP first (Slice 1 only)

1. Phase 1 and Phase 2.
2. Phase 3 (US2): admin creates agent accounts.
3. Phase 4 (US1): agent signs in with TOTP and actions are attributed. The assignment and status tasks wait on D6 and D7, so the MVP is partial until those are decided.
4. Stop and validate with quickstart Slice 1.

### Incremental delivery

1. Slice 1 (Phases 1 to 4), after C1 and D3 are recorded.
2. Slice 2 (Phases 5 to 10), behind the flag, after D4 for a real provider, D10 for launch, and D13 and D9 for the blocked behaviors.
3. Slice 3 (Phase 11), after D11.

## Notes

- Do not start a `[BLOCKED: X]` task until X is resolved. A blocked test may be written early if it is marked as such.
- Keep the access-matrix and cross-user tests in the same commit as the endpoint they cover (Constitution IV).
- Stop at each checkpoint and run the matching quickstart section before continuing.
- Fixes to the contract (T086) and quickstart (T147) are documentation corrections that the code must follow.
