# Data Model: User Authentication for the Caramel AI Support Chat

**Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md) | **Research**: [research.md](research.md)

Storage is the existing embedded H2 file database through Spring Data JDBC. Identifiers are application-generated UUIDs, written through `persistence/Store` (CLAUDE.md). Timestamps are UTC instants. Email addresses are stored normalized (trimmed, lowercased). Tokens and codes are never stored in plain form.

## New entities

### UserAccount (`user_account`)

A person who can sign in (spec: User Account).

| Field | Type | Rules |
|---|---|---|
| `id` | UUID | Primary key. |
| `email` | string(320) | Required, unique, normalized. Never logged or returned to other users. |
| `role` | enum `CUSTOMER`, `AGENT`, `ADMIN` | Required. Read on every request (R2). |
| `status` | enum `ACTIVE`, `DISABLED` | Required. `DISABLED` ends every session at once (FR-030). |
| `passwordHash` | string(100), nullable | `{bcrypt}…`. Null until a password is set (activation for agents and admins; sign-up sets it for customers). Never returned by any API. |
| `emailVerifiedAt` | instant, nullable | Customers: set by verification. Agents and admins: set by activation, because the activation link proves mailbox access. |
| `mfaSecretEncrypted` | string(255), nullable | AES-256-GCM ciphertext of the TOTP secret. Required for `AGENT` and `ADMIN`. |
| `mfaEnrolledAt` | instant, nullable | Set by the first valid TOTP code at sign-in. |
| `mfaLastStep` | long, nullable | Last accepted TOTP time step. Rejects replay. |
| `failedSignInCount` | integer | Consecutive failures. Reset on success. |
| `nextAttemptAt` | instant, nullable | Progressive delay boundary (R6). |
| `lockedUntil` | instant, nullable | Lockout boundary (5 failures, 15 minutes). |
| `createdAt` | instant | Set on creation. |
| `createdByUserId` | UUID, nullable | Admin who created an agent or admin account. Null for customers and the bootstrap admin. |
| `updatedAt` | instant | Set on each change. |

**Validation**: `email` format check. Password: at least 12 characters, at most 72 UTF-8 bytes, not on the common-password list (checked before hashing). Role change to or from `ADMIN` requires the actor to be an active admin, and the last active admin cannot be disabled or demoted (spec Edge Cases).

### SignInSession (`sign_in_session`)

One signed-in period on one device (spec: Sign-In Session).

| Field | Type | Rules |
|---|---|---|
| `id` | UUID | Primary key. Carried as the `sid` claim. |
| `userId` | UUID | FK to UserAccount. Required. |
| `createdAt` | instant | Sign-in time. |
| `expiresAt` | instant | `createdAt` + 7 days. Absolute, not sliding (R3). |
| `revokedAt` | instant, nullable | Set once. |
| `revokeReason` | enum, nullable | `SIGN_OUT`, `SIGN_OUT_ALL`, `PASSWORD_RESET`, `ACCOUNT_DISABLED`, `ACCOUNT_DELETED`, `TOKEN_REUSE`. |

A session is active when `revokedAt` is null, `expiresAt` is in the future, and its account is `ACTIVE`. That check runs on every authenticated request (R2, plan C2).

### RefreshToken (`refresh_token`)

One issued refresh credential within a session (research R3).

| Field | Type | Rules |
|---|---|---|
| `id` | UUID | Primary key. |
| `sessionId` | UUID | FK to SignInSession. Required. |
| `tokenHash` | char(64) | SHA-256 hex of the opaque token. Unique. |
| `issuedAt` | instant | Set on creation. |
| `rotatedAt` | instant, nullable | Set when a refresh replaces this token. A rotated token is never valid again. |

### OneTimeToken: `password_reset_token` (`password_reset_token`)

Used for password reset and for agent or admin activation. Single-use and time-limited (spec: Password Reset Request).

| Field | Type | Rules |
|---|---|---|
| `id` | UUID | Primary key. |
| `userId` | UUID | FK to UserAccount. Required. |
| `purpose` | enum `RESET`, `ACTIVATION` | Required. |
| `tokenHash` | char(64) | SHA-256 hex. Unique. |
| `createdAt` | instant | |
| `expiresAt` | instant | `RESET`: 30 minutes (FR-009). `ACTIVATION`: 24 hours (planning default, not in the spec). |
| `consumedAt` | instant, nullable | Set on use. Also set on supersession: a newer token of the same purpose consumes the older ones (only the most recent is valid, FR-009). |

### EmailVerificationToken (`email_verification_token`)

Confirms a customer's email address (spec: Email Verification Request).

| Field | Type | Rules |
|---|---|---|
| `id` | UUID | Primary key. |
| `userId` | UUID | FK to UserAccount. Required. |
| `tokenHash` | char(64) | SHA-256 hex. Unique. |
| `createdAt` | instant | |
| `expiresAt` | instant | 24 hours (spec Assumptions). |
| `consumedAt` | instant, nullable | Set on use or supersession. |

### AuditEvent (`audit_event`)

An auth event or privileged action (spec: Audit Record).

| Field | Type | Rules |
|---|---|---|
| `id` | UUID | Primary key. |
| `occurredAt` | instant | Indexed. |
| `requestId` | string(64), nullable | From `RequestIdFilter`. |
| `actorUserId` | UUID, nullable | Acting account. Null for anonymous attempts and for the shared token. |
| `actorKind` | enum `USER`, `ANONYMOUS`, `SHARED_TOKEN`, `SYSTEM` | Required. |
| `eventType` | string(48) | Fixed vocabulary (below). |
| `subjectUserId` | UUID, nullable | Account acted on, for admin actions and sign-in attempts against a known account. |
| `targetType` | string(32), nullable | `ESCALATION`, `SESSION`, `ACCOUNT`. |
| `targetId` | string(64), nullable | UUID of the target. |
| `detail` | string(128), nullable | From a fixed list only, e.g. `role=ADMIN`, `reason=LOCKED`. Never an email, token, or password. |

**Event vocabulary**: `SIGN_IN_SUCCESS`, `SIGN_IN_FAILURE`, `SIGN_IN_LOCKED`, `MFA_FAILURE`, `SIGN_UP_ACCEPTED`, `SIGN_UP_DUPLICATE`, `EMAIL_VERIFIED`, `RESET_REQUESTED`, `RESET_COMPLETED`, `ACTIVATION_COMPLETED`, `SESSION_ENDED`, `SESSIONS_ENDED_ALL`, `TOKEN_REUSE_DETECTED`, `ACCOUNT_CREATED`, `ACCOUNT_DISABLED`, `ROLE_CHANGED`, `ACCOUNT_DELETED`, `ESCALATION_VIEWED`, `ESCALATION_ASSIGNED`, `ESCALATION_STATUS_CHANGED`, `ESCALATION_REPLIED`, `SHARED_TOKEN_USED`, `EMAIL_SEND_FAILED`.

**Retention**: 90 days by default (research R14).

### EscalationReply (`escalation_reply`)

An agent's reply to an escalation (spec: Escalation Request gains actions).

| Field | Type | Rules |
|---|---|---|
| `id` | UUID | Primary key. |
| `escalationId` | UUID | FK to EscalationRequest. Required. |
| `authorUserId` | UUID, nullable | The agent. Null when the author was the shared token (Phase 1 only). |
| `authorKind` | enum `USER`, `SHARED_TOKEN` | Required. |
| `text` | CLOB | 1 to 2,000 characters after trimming. |
| `createdAt` | instant | |

## Changed entities

### ChatSession (`chat_session`): additions

| Field | Change | Rules |
|---|---|---|
| `userId` | Added, nullable | FK to UserAccount, `ON DELETE SET NULL`. Set once at creation (signed-in, verified customer, flag on) or at claim (R7). Never reassigned. |
| `claimedAt` | Added, nullable | Set with the claim. A non-null `userId` set by creation has `claimedAt` null. |

`userRef` is unchanged and is never an identity (R17). Ownership rules: a session with a non-null `userId` is readable, writable, and ratable only by that account (404 otherwise). A null `userId` keeps the v1 rule.

### EscalationRequest (`escalation_request`): additions and changes

| Field | Change | Rules |
|---|---|---|
| `sessionId` | Now nullable | FK `ON DELETE SET NULL`. Cleared when history is deleted (R13). |
| `customerUserId` | Added, nullable | FK to UserAccount, `ON DELETE SET NULL`. Verified identity at request time (FR-019). Null for anonymous requests. |
| `customerEmail` | Added, nullable | Verified email at request time. Cleared on account deletion or history deletion for opened escalations (R13). |
| `assignedAgentId` | Added, nullable | FK to UserAccount, `ON DELETE SET NULL`. Set by an admin (R12). |
| `assignedAt` | Added, nullable | |
| `firstOpenedAt` | Added, nullable | Set on the first agent view. Governs deletion (R13). |
| `status` | Vocabulary extended | `NEW`, `ACKNOWLEDGED`, `CLOSED` (R12, D7). `transcript`, `reason`, and `requestId` are unchanged and immutable. |

## Relationships

```text
UserAccount 1 ──< SignInSession 1 ──< RefreshToken
UserAccount 1 ──< OneTimeToken            (RESET, ACTIVATION)
UserAccount 1 ──< EmailVerificationToken
UserAccount 1 ──< ChatSession             (optional: ownership, set once)
UserAccount 1 ──< EscalationRequest       (customer identity, optional)
UserAccount 1 ──< EscalationRequest       (assigned agent, optional)
EscalationRequest 1 ──< EscalationReply
UserAccount 1 ──< AuditEvent              (actor and subject, optional)
ChatSession 1 ──< Message 1 ──0..1 ResponseRating   (unchanged)
```

## State transitions

**UserAccount, lifecycle**

- Customer: `SIGNED_UP (unverified, no email verified)` → `VERIFIED` (verification token consumed).
- Agent or admin: `PENDING (no password)` → `ACTIVATED (password set, email verified)` → `MFA_ENROLLED (first valid code)`. Admin can re-issue an activation link while `PENDING` or `ACTIVATED` with `mfaEnrolledAt` null.
- `status`: `ACTIVE` ⇄ `DISABLED`. Disabling revokes every session (`ACCOUNT_DISABLED`). Re-enabling does not restore sessions.
- `role`: `AGENT` ⇄ `ADMIN`. Applies on the next request. Last active admin guard: the change is refused when it would leave zero active admins.

**SignInSession**: `ACTIVE` → `REVOKED` (reason recorded) or `EXPIRED` (`expiresAt` passed). Ended states are final.

**RefreshToken**: `ISSUED` → `ROTATED` (at refresh). `ROTATED` tokens are refused after the 10-second grace window and trigger `TOKEN_REUSE` (R3).

**OneTimeToken / EmailVerificationToken**: `ISSUED` → `CONSUMED` (used) or `CONSUMED` (superseded by a newer token of the same purpose) or `EXPIRED`. Consumption is one conditional update (`WHERE consumed_at IS NULL AND expires_at > now`), so the same link cannot be used twice even under concurrent requests.

**Lockout**: `CLEAR` → `DELAYED` (2 to 4 failures; `nextAttemptAt` in future) → `LOCKED` (5 failures; `lockedUntil` in future for 15 minutes) → `CLEAR` (on success, or when the lock expires with the counter reset).

**EscalationRequest.status**: `NEW` → `ACKNOWLEDGED` (first agent view or status change) → `CLOSED`. `CLOSED` → `ACKNOWLEDGED` only by an admin.

**EscalationRequest assignment**: `UNASSIGNED` ⇄ `ASSIGNED(agent)`. Reassignment replaces the agent. Each change is audited.

## Derived checks and queries

| Spec criterion | Query idea |
|---|---|
| FR-017, SC-004 cross-user | Every owned-resource query filters by `userId` (or `assignedAgentId` for agents). A miss and a foreign hit both return 404. |
| FR-025 agent visibility | Agent escalation list: `assignedAgentId = :me`. Admin list: all. |
| SC-003 attribution | Count of `audit_event` rows for agent actions with `actorKind = SHARED_TOKEN` after the migration window must be 0. |
| SC-010 log review | `LogLeakTest` and a test that scans `audit_event.detail` and logs for emails and token shapes. |
| FR-036 retention | Rows with `occurredAt` older than 90 days are deleted by `AuditRetentionJob`. |

## Schema notes

- `schema.sql` gets `CREATE TABLE IF NOT EXISTS` for the new tables, and `ALTER TABLE … ADD COLUMN IF NOT EXISTS` for `chat_session.user_id` and `claimed_at` and the new `escalation_request` columns.
- `escalation_request.session_id` changes from NOT NULL to nullable. The existing dev file needs the ALTER or a reset (research R11).
- Foreign keys from `chat_session.user_id`, `escalation_request.customer_user_id`, and `escalation_request.assigned_agent_id` use `ON DELETE SET NULL`. Deletion code still removes owned rows explicitly so that no transcript keeps identity (R13).
- Indexes: `user_account(email)` unique; `sign_in_session(user_id)`; `refresh_token(token_hash)` unique and `refresh_token(session_id)`; `password_reset_token(token_hash)` unique, `(user_id, purpose)`; `email_verification_token(token_hash)` unique; `chat_session(user_id, created_at)`; `escalation_request(assigned_agent_id, created_at)`; `audit_event(occurred_at)`.
- No column stores passwords, tokens, the TOTP secret in plain form, or the signing and encryption keys.
