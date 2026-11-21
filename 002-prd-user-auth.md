# PRD 002: User Authentication for the Caramel AI Support Chat

Written for: engineers and the product owner planning the follow-up to v1 (see [PRD.md](PRD.md)).

## 1. Objective

Let customers sign in to the support chat so conversations are tied to a verified identity, history persists across devices and sessions, and support agents are authenticated individually instead of through one shared token. Anonymous chat remains available for basic questions.

## 2. Background

v1 ([PRD.md](PRD.md)) deliberately left authentication out of scope:
- Customers chat anonymously; a session is identified only by a session ID.
- Support agents call `/api/agent/**` with a single shared secret in the `X-Agent-Token` header (`AgentTokenFilter`). There is no per-agent identity, no audit trail of who acted, and rotating the secret affects everyone.
- Escalated conversations cannot be linked to a Caramel AI account, so agents must ask the customer who they are.

## 3. Users

- **Customers** with a Caramel AI account who want account-aware support and a persistent chat history.
- **Anonymous visitors** who only need general answers (unchanged from v1).
- **Support agents** who review and respond to escalations.
- **Admins** who manage agent access.

## 4. Scope

### In scope
- Customer sign-up, sign-in, sign-out, and password reset.
- Optional sign-in: anonymous chat still works; signing in unlocks history and account-linked escalation.
- Linking an in-progress anonymous session to the account when the user signs in.
- Per-agent accounts for `/api/agent/**`, replacing the shared `X-Agent-Token`.
- Role-based access: `CUSTOMER`, `AGENT`, `ADMIN`.
- Session management (expiry, sign-out, revoke all sessions).
- Rate limiting and lockout on auth endpoints.

### Out of scope
- Social or enterprise SSO (Google, GitHub, SAML) — candidate for a later phase.
- Multi-factor authentication for customers (agents and admins: see open questions).
- Billing or account-changing actions in the chat (still out of scope per PRD 001).
- Migrating Caramel AI's main product accounts; this PRD assumes an identity source decision (see section 9).

## 5. User Stories

1. As a customer, I can create an account with email and password so my chats are saved.
2. As a customer, I can sign in and see my previous conversations.
3. As a customer, if I start chatting anonymously and then sign in, that conversation is attached to my account.
4. As a customer, I can reset a forgotten password by email.
5. As a customer, I can sign out and revoke my other active sessions.
6. As a support agent, I sign in with my own credentials and every action I take is attributed to me.
7. As an agent, I see the verified account (email, account ID) on an escalated conversation.
8. As an admin, I can create, disable, and change the role of agent accounts.

## 6. Functional Requirements

### Customer authentication
1. Sign-up with email and password. Email must be verified before history is saved to the account.
2. Passwords: minimum 12 characters, checked against a common-password list, stored with Argon2id or bcrypt. Never logged or returned by any API.
3. Sign-in issues a short-lived access token (about 15 minutes) and a rotating refresh token (about 7 days). The refresh token is stored in an `HttpOnly`, `Secure`, `SameSite=Lax` cookie; the access token is held in memory only, never in `localStorage`.
4. Sign-out invalidates the refresh token server-side. "Sign out everywhere" invalidates all of the user's refresh tokens.
5. Password reset uses a single-use, time-limited (30 minutes) token sent by email. Reset invalidates all existing sessions. Responses must not reveal whether an email is registered.
6. Repeated failed sign-ins trigger progressive delay and temporary lockout; auth endpoints are rate limited per IP and per account.

### Chat integration
7. `POST` session and message endpoints accept an optional authenticated identity. With no credentials they behave exactly as in v1.
8. A signed-in user can list and reopen their own past sessions. A user can never read another user's sessions (return 404, not 403, for foreign IDs).
9. When an anonymous session holder signs in, the active session is claimed by the account. Claiming requires possession of the session ID plus the new valid sign-in, and a session can be claimed only once.
10. Escalations from a signed-in user carry the verified user ID and email; anonymous escalations behave as in v1.
11. Users can delete their chat history and account; deletion removes sessions, messages, and ratings linked to the account.

### Agent authentication and authorization
12. `/api/agent/**` requires an authenticated agent account with role `AGENT` or `ADMIN`. The shared `X-Agent-Token` is removed after a migration window (see section 10).
13. Agent actions (view escalation, change status, reply) are recorded with the agent's user ID and timestamp.
14. Admins can create, disable, and re-role agent accounts. Disabling an account revokes its sessions immediately.
15. Agent and admin sign-in requires MFA (TOTP) — pending confirmation in open questions.

### Authorization matrix

| Resource | Anonymous | Customer | Agent | Admin |
|---|---|---|---|---|
| Chat (own session) | yes | yes | yes | yes |
| Past sessions list | no | own only | assigned escalations | all |
| Request escalation | yes | yes | n/a | n/a |
| `/api/agent/**` | no | no | yes | yes |
| Manage agent accounts | no | no | no | yes |

## 7. Technical Requirements

| Layer | Choice |
|---|---|
| Backend | Spring Boot with Spring Security (stateless resource server) |
| Tokens | JWT access tokens (signed, short-lived); opaque rotating refresh tokens stored hashed in the database |
| Password hashing | Spring Security `DelegatingPasswordEncoder` with Argon2id or bcrypt |
| Frontend | React + TypeScript: auth context, sign-in/sign-up/reset screens, silent refresh |
| Persistence | New tables: `user_account`, `refresh_token`, `password_reset_token`, `email_verification_token`; add nullable `user_id` to `chat_session` |
| Email | Transactional email provider for verification and reset (provider TBD) |
| Secrets | JWT signing key and email credentials in `.env`, never committed; add placeholders to `.env.example` |

Notes:
- Existing `CorsConfig` must allow credentials for the frontend origin only (no wildcard).
- The browser bundle must contain no signing keys or secrets; the existing bundle-secrets test should be extended to cover new variables.
- `RequestIdFilter` logging must be extended so emails, tokens, and passwords never appear in logs.

## 8. Non-Functional Requirements

- **Security:** follow OWASP ASVS Level 2 for authentication; CSRF protection on cookie-authenticated endpoints; constant-time comparisons for tokens; security headers set.
- **Privacy:** store only what is needed (email, password hash, timestamps). Provide account and data deletion. Auth logs retained for a bounded period.
- **Performance:** token validation adds under 5 ms per request; sign-in under 500 ms at p95.
- **Availability:** anonymous chat must keep working if the email provider or auth endpoints are degraded.
- **Accessibility:** auth forms are keyboard and screen-reader friendly with clear error messages that do not leak account existence.
- **Observability:** log auth events (sign-in success/failure, lockout, reset requested, role change) with request ID and user ID, never credentials.

## 9. Success Metrics

- At least 40% of returning customers sign in within the first month of launch.
- Fewer than 1% of sign-in attempts fail for reasons other than wrong credentials.
- 100% of agent actions attributed to an individual account after the migration window.
- Zero cross-user data exposure incidents; automated authorization tests cover every endpoint in the matrix above.
- Median escalation handling time drops compared with v1 because agents no longer have to ask who the customer is.

## 10. Rollout

1. **Phase 1 – Agents:** introduce agent accounts and sign-in alongside the shared token; both accepted.
2. **Phase 2 – Customers:** release sign-up/sign-in, history, and session claiming behind a feature flag.
3. **Phase 3 – Cleanup:** remove `X-Agent-Token` support and `AGENT_API_TOKEN` from configuration and docs.

## 11. Open Questions

- What is the identity source: a new standalone user store for the chat, or Caramel AI's existing account system (which would favor OIDC)?
- Is email plus password enough for customers, or should magic-link / passwordless sign-in be offered first?
- Should MFA be mandatory for agents and admins at launch, and optional for customers?
- What email provider is approved for verification and reset messages?
- How long are chat histories retained for signed-in users, and what does account deletion cascade to?
- Should agents see all escalations or only ones assigned to them?
- Is there a compliance requirement (GDPR, SOC 2) that dictates logging or retention beyond the defaults above?
