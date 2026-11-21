# Quickstart: Validating User Authentication

**Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md) | **Contract**: [contracts/auth-api.yaml](contracts/auth-api.yaml) | **Data**: [data-model.md](data-model.md)

This guide runs the feature end to end and proves each slice. It does not repeat the implementation. Test and task details belong in `tasks.md`.

## Prerequisites

- JDK 21, Maven 3.9+, Node 22 with npm (as in CLAUDE.md).
- A repo-root `.env` copied from `.env.example`. New variables (research R16):

  ```bash
  AUTH_JWT_SIGNING_KEY=<base64, 32 or more random bytes>
  AUTH_TOTP_ENCRYPTION_KEY=<base64, 32 random bytes>
  AUTH_CUSTOMER_SIGNIN_ENABLED=false      # keep off until Slice 2 is accepted
  AUTH_BOOTSTRAP_ADMIN_EMAIL=admin@example.test   # first run only
  APP_PUBLIC_BASE_URL=http://localhost:5173
  ```

  Generate keys with a standard CSPRNG tool, for example `openssl rand -base64 32`. Do not commit `.env`.
- Existing dev database: the v1 file under `backend/data/` predates the schema change. Delete `backend/data/support*` before first run, or apply the ALTER statements in data-model.md. Pilot data, if any, must be preserved by the ALTER route.

## Run

```bash
./run.sh                                  # backend + frontend + Chrome, as in v1
```

Or separately:

```bash
cd backend && mvn spring-boot:run         # http://localhost:8080
cd frontend && npm run dev                # http://localhost:5173
```

Startup must fail clearly when `AUTH_JWT_SIGNING_KEY` or `AUTH_TOTP_ENCRYPTION_KEY` is missing. This is a required check, not a warning.

## Test suites

```bash
cd backend && mvn test                    # default offline suite, includes auth unit and MockMvc tests
cd backend && mvn -Dtest=AuthApiTest test # one class
cd frontend && npm test                   # vitest, includes auth screens and bundle secret check
cd frontend && npm run build              # tsc and vite build (bundle secret check runs on dist/)
```

The default suite must not call the real model or email provider. Auth tests use the dev outbox (`OutboxEmailSender`) and a fixed clock.

## Verification

### Slice 1 — Agents (Phase 1)

1. **Bootstrap**: Start with `AUTH_BOOTSTRAP_ADMIN_EMAIL` set. The console shows one activation link. Restart. No second link is shown (R18).
2. **Activate admin**: Open the link in the browser. Set a password of 12+ characters. The app asks for a TOTP code; enter the code from the authenticator. First code enrolls it (R5). Sign in again with password and code.
3. **Create agent**: As admin, `POST /api/admin/accounts` with role `AGENT`. The response includes one activation link. Repeat the activation steps for the agent, on a second browser profile.
4. **Lockout**: Five wrong passwords for the agent account. The sixth attempt is refused with the same body as a wrong password, and the account is locked for 15 minutes (spec US3 AC6). Check the audit row `SIGN_IN_LOCKED` with the agent ID and no email.
5. **Disable**: Sign in as the agent on two devices. Disable the agent as admin. Both devices' next requests fail at once. New sign-in fails. Each event is audited with the admin's ID.
6. **Last admin**: Try to disable or demote the only active admin. Expect 409.
7. **Assignment and actions**: Assign an escalation to the agent. The agent sees it in `GET /api/agent/escalations`, and does not see an unassigned one. Change status, then reply. Check `audit_event` rows for `ESCALATION_VIEWED`, `ESCALATION_STATUS_CHANGED`, and `ESCALATION_REPLIED`, each with the agent's user ID and a timestamp (SC-003).
8. **Shared token (Phase 1)**: `X-Agent-Token` still works. Its actions are audited with `actorKind = SHARED_TOKEN` and no actor.

### Slice 2 — Customers (Phase 2, flag on)

Set `AUTH_CUSTOMER_SIGNIN_ENABLED=true` for the test environment only.

1. **Sign-up**: Sign up with a new address and a 12+ character password. The response is 202. The dev outbox (`backend/data/outbox/`) holds one verification link. Open it. The account is verified.
2. **Registered address**: Sign up again with the same address. The response is 202 with the same body, and a duplicate notice goes to the outbox. No account is created (FR-011, SC-009).
3. **Weak password**: A 11-character or common password returns 400 with the rule in the message, no account hint.
4. **Unverified chat**: Sign in unverified, create a chat session. The session has no owner. Verify, create another session. That session has an owner (FR-002).
5. **Claim**: Start an anonymous chat, send one message, sign in with `claimSessionId`. The response says `claimed: true`. Sign in again with the same session ID and a second account. The response says `claimed: false`, `reason: already_claimed`. Both attempts do not reveal foreign existence (R7).
6. **Cross-user**: As customer B, open customer A's session through `/api/me/sessions/{id}`, the message endpoint, the rating endpoint, and the escalation endpoint. Each returns 404 with the same body as an unknown ID (FR-017, SC-004).
7. **Escalation identity**: Customer asks for a human. The agent's detail view shows the verified email and account ID (FR-028). Anonymous escalation shows no identity (FR-019).
8. **Reset**: Request a reset twice. Only the second link works. Use it once (success) and try again (refused). Sign-in sessions from before the reset stop working (FR-010). Reset for an unknown email returns the same 202.
9. **Sign out everywhere**: Sign in on three browser profiles. Call sign-out-everywhere from one. All three stop (FR-008).
10. **Refresh reuse**: Capture a refresh cookie, refresh once, then replay the old cookie after more than 10 seconds. The session family ends and an audit event `TOKEN_REUSE_DETECTED` is written (R3). Replay within 10 seconds is refused without revoking.
11. **Delete history and account**: Delete history. Sessions, messages, and ratings are gone; unopened escalations are gone; opened ones keep the transcript with identity cleared (R13). Delete the account with the password. Sign-in is refused.
12. **Feature flag off**: Set the flag to false. Customer sign-in, sign-up, and history endpoints return 404. Anonymous chat is unchanged (FR-037).

### Slice 3 — Cleanup (Phase 3)

1. `X-Agent-Token` returns 404 or 401 everywhere it was accepted (SC-011). The old token tests are removed.
2. `AGENT_API_TOKEN` is absent from `.env.example`, `application.yml`, and docs.
3. Every agent action in the audit table has an actor (SC-003).

## Checks that apply to every slice

- **Access matrix**: One automated test per row of the matrix in the spec, covering anonymous, customer, agent, and admin, plus a cross-user case for every owned resource (Constitution IV).
- **Logs**: `LogLeakTest` fails if any log line or audit `detail` contains an email, password, access or refresh token, reset or activation token, TOTP secret, or signing key (SC-010).
- **Bundle**: `npm run build` followed by the bundle secret test finds none of `AUTH_JWT_SIGNING_KEY`, `AUTH_TOTP_ENCRYPTION_KEY`, or `EMAIL_PROVIDER_API_KEY` values in `dist/`.
- **CORS**: A request from another origin with credentials is refused. The allowed origin gets the expected headers.
- **Anonymous chat during auth outage**: Stop the account lookup (test double that throws) and confirm anonymous sessions and messages still return v1 responses (FR-014, SC-006).

## Performance

Run on the target host with the bcrypt cost chosen for it (R4):

- Sign-in p95 under 500 ms at normal load (SC-007). Measure with 50 sequential and 10 concurrent sign-ins.
- Authenticated request overhead p95 under 5 ms (SC-008). Measure `GET /api/auth/me` against an anonymous `GET` of the same cost, after warm-up.
- If sign-in is slow, lower the bcrypt cost only after checking that the dummy-hash path still matches the real cost (R4). Never lower it below the cost used for stored hashes without a re-hash plan.

## Troubleshooting

- **Refresh fails on reload**: The cookie is `Secure`. Use `http://localhost`, which browsers treat as secure, not a LAN IP. The Vite proxy keeps `/api` same-origin.
- **401 on chat after sign-in**: The access token expired. The client should refresh once. A 401 that persists means the session was revoked (sign-out, disable, or reset).
- **`mfa-required` on agent sign-in**: Send `totpCode` from the enrolled authenticator. Clock drift beyond one 30-second step causes a refusal.
- **No activation link printed**: An admin already exists. Use `POST /api/admin/accounts/{id}/activation-links` as an admin, or remove the bootstrap variable.
