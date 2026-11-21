# Feature Specification: User Authentication for the Caramel AI Support Chat

**Feature Branch**: `002-user-auth`

**Created**: 2026-11-21

**Status**: Draft

**Input**: User description: "Create spec 002 from 002-prd-user-auth.md, governed by .specify/memory/constitution.md (v2.0.0). This spec supersedes FR-017 and the personalization out-of-scope line of specs/001-support-chat-assistant/spec.md, and the agent-wide visibility in 001 FR-009 for agents."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Agent Signs In Individually and Every Action Is Attributed (Priority: P1)

A Caramel AI support agent signs in with their own account and works on escalations. Viewing an escalation, changing its status, and replying are each recorded against that agent's account and the time of the action, so the team knows who did what. Agent sign-in requires a second verification factor in addition to the password.

**Why this priority**: The shared agent secret makes it impossible to attribute actions to a person, and the constitution prohibits shared credentials for human access. This is the first rollout phase and must exist before the shared secret can be retired.

**Independent Test**: Sign in as an agent account, open an escalation, change its status, and send a reply. Confirm that the audit record shows that account and the time for each action.

**Acceptance Scenarios**:

1. **Given** an active agent account exists, **When** the agent signs in with their password and second verification factor, **Then** they reach the escalation queue.
2. **Given** an agent is signed in, **When** they reply to an escalation, **Then** the reply is recorded with their account identifier and the time.
3. **Given** an agent enters a wrong second verification factor, **When** they submit it, **Then** access is refused and the failed attempt is recorded.
4. **Given** a visitor who is not signed in, or a customer account, **When** they try to open agent escalation functions, **Then** access is refused.

---

### User Story 2 - Admin Manages Agent Accounts (Priority: P1)

An admin creates agent accounts, changes their roles, and disables accounts that should no longer have access. Disabling an account ends its active sessions immediately.

**Why this priority**: No agent can have an individual account until an admin creates one. This is a prerequisite for User Story 1.

**Independent Test**: As an admin, create an agent account, change its role, and then disable it while it is signed in on two devices. Confirm that both sessions stop working at once and that the account cannot sign in again.

**Acceptance Scenarios**:

1. **Given** an admin is signed in, **When** they create an agent account, **Then** the new account exists, is active, and the creation is recorded with the admin's identifier and the time.
2. **Given** an agent is signed in on two devices, **When** an admin disables the account, **Then** both sessions stop working immediately and new sign-in attempts are refused.
3. **Given** an admin changes an agent account to the ADMIN role, **When** the change is saved, **Then** the new role applies no later than the account's next request, and the change is recorded with the admin's identifier and the time.
4. **Given** a signed-in agent who is not an admin, **When** they try to create, disable, or change the role of an account, **Then** the request is refused.

---

### User Story 3 - Customer Creates an Account, Verifies Email, and Signs In and Out (Priority: P2)

A customer creates an account with an email address and a password, verifies the address, and then signs in and out. Signed-in customers get saved chat history and account-linked support. Customer sign-in is turned on with the customer feature control described in the rollout.

**Why this priority**: Accounts unlock persistent history and account-aware escalation, which are the customer goals of this release. They depend on the agent work only for escalation handling.

**Independent Test**: Sign up with a new email address, verify it through the link sent to that address, sign in, sign out, and sign in again. Confirm the signed-in state at each step.

**Acceptance Scenarios**:

1. **Given** a visitor, **When** they sign up with an email address and a password of at least 12 characters that is not on the common-password list, **Then** an account is created and a verification message is sent to that address.
2. **Given** a customer has not verified their email address, **When** they chat while signed in, **Then** the conversation is not saved to the account until the address is verified, and the customer is told how to verify.
3. **Given** a visitor enters a password under 12 characters or one on the common-password list, **When** they sign up, **Then** sign-up is refused with a message that explains the rule and does not reveal whether the email address is already registered.
4. **Given** a customer is signed in, **When** they sign out, **Then** their session ends on the server and its credentials no longer work.
5. **Given** a visitor enters an email address and password that do not match an account, **When** they sign in, **Then** sign-in is refused with the same message whether or not the email address is registered.
6. **Given** an account has five consecutive failed sign-in attempts, **When** a sixth attempt is made, **Then** the account is locked for 15 minutes, and the lockout is recorded.

---

### User Story 4 - Customer Resets a Forgotten Password and Manages Sessions (Priority: P2)

A customer who has forgotten their password requests a reset by email and sets a new one through a single-use link. A customer can also end all of their sessions at once.

**Why this priority**: Without password reset, customers who forget their password lose their history. Session control is required to limit the effect of a lost device.

**Independent Test**: Request a reset, use the link to set a new password, and confirm the old session stops working. Separately, sign in on three devices and use "sign out everywhere" on one of them.

**Acceptance Scenarios**:

1. **Given** a visitor requests a password reset for any email address, **When** the request is submitted, **Then** the visible response is identical whether or not an account exists for that address.
2. **Given** a valid reset link that was sent less than 30 minutes ago, **When** the customer sets a new password, **Then** signing in with the new password works, and every session that existed before the reset stops working.
3. **Given** a reset link that has already been used, or was sent more than 30 minutes ago, **When** the customer tries to use it, **Then** the reset is refused and the customer is offered a new reset request.
4. **Given** a customer is signed in on three devices, **When** they choose "sign out everywhere", **Then** all three sessions stop working.

---

### User Story 5 - Signed-In Customer Sees and Reopens Their Own Conversations (Priority: P2)

A signed-in customer sees a list of their own past conversations and can reopen one to review it or continue it.

**Why this priority**: Persistent history is the main benefit of signing in for customers, and it depends on User Story 3.

**Independent Test**: Sign in as customer A, have a conversation, sign out, sign in as customer B, and confirm that customer B cannot see or open customer A's conversation.

**Acceptance Scenarios**:

1. **Given** a signed-in customer has saved conversations, **When** they open their history, **Then** they see only their own conversations, most recent first.
2. **Given** a signed-in customer opens one of their conversations, **When** the conversation loads, **Then** its earlier messages appear and the customer can continue chatting in it.
3. **Given** a signed-in customer asks for a conversation that belongs to another customer, **When** the request is made, **Then** the response is the same as for a conversation that does not exist, and no content is shown.
4. **Given** a visitor who is not signed in, **When** they use the chat, **Then** no history list is offered and the chat behaves as in v1.

---

### User Story 6 - Anonymous Conversation Is Attached to the Account at Sign-In (Priority: P3)

A visitor who has been chatting anonymously signs in during the same session, and the conversation in progress is attached to their account.

**Why this priority**: This lets a customer keep a conversation they started before signing in, but the rest of the customer experience works without it.

**Independent Test**: Start an anonymous chat, sign in, and confirm the conversation appears in the account's history. Then try to attach the same conversation to a second account and confirm that it is refused.

**Acceptance Scenarios**:

1. **Given** an anonymous conversation is in progress, **When** its holder signs in, **Then** the conversation is attached to the account and appears in that account's history.
2. **Given** a conversation is already attached to an account, **When** another user tries to attach it, **Then** the attempt is refused with the same response as for a conversation that does not exist.
3. **Given** a conversation has been attached once, **When** the same holder tries to attach it again, **Then** the attempt is refused.
4. **Given** the customer started a new chat before signing in, **When** they sign in, **Then** no earlier conversation is attached.

---

### User Story 7 - Escalations From Signed-In Customers Carry Verified Identity (Priority: P2)

When a signed-in customer asks for a human agent, the escalation carries their verified account identity. The agent sees it and does not need to ask the customer who they are.

**Why this priority**: This removes a step from every account-linked escalation and is one of the stated measures of success for this release.

**Independent Test**: Sign in as a customer, ask for a human agent, and confirm that the agent's view of the escalation shows the verified email address and account identifier. Repeat anonymously and confirm the escalation has no account identity.

**Acceptance Scenarios**:

1. **Given** a signed-in customer asks for a human agent, **When** the escalation is recorded, **Then** it holds the customer's verified account identifier and email address, along with the transcript and time.
2. **Given** an agent opens an account-linked escalation, **When** the escalation loads, **Then** the verified email address and account identifier are shown.
3. **Given** an anonymous visitor asks for a human agent, **When** the escalation is recorded, **Then** it holds no account identity and behaves as in v1.

---

### User Story 8 - Customer Deletes Chat History or Account (Priority: P3)

A signed-in customer can delete their chat history without deleting their account, or delete their account entirely.

**Why this priority**: Deletion is a privacy requirement, but it applies only once accounts and history exist.

**Independent Test**: Delete chat history and confirm the conversations are gone. Delete an account and confirm that sign-in is refused and the account's conversations are gone.

**Acceptance Scenarios**:

1. **Given** a signed-in customer chooses to delete their chat history, **When** the deletion completes, **Then** their conversations, messages, and ratings are removed and no longer appear in any screen.
2. **Given** a signed-in customer chooses to delete their account, **When** the deletion completes, **Then** all of their sessions end, sign-in is refused, and their conversations, messages, and ratings are removed.
3. **Given** a deleted account had an escalation that an agent has already received, **When** the agent opens it, **Then** the transcript remains and the customer's identity fields are removed (see Assumptions).

---

### Edge Cases

- What happens when a sign-in attempt is made during a lockout? It is refused with the same message as a wrong password, and the lockout ends after its 15-minute period.
- What happens when a renewal credential that has already been replaced is presented again? This is treated as possible theft: all sessions of that account end, and the customer must sign in again.
- What happens when a customer signs out in one browser tab while another tab is still signed in? The other tab stops being signed in no later than its next renewal.
- What happens when a customer requests a second reset? Only the most recently sent reset link works; earlier links are refused.
- What happens when the email provider is unavailable? Sign-up, verification, and reset show a friendly message that email cannot be sent right now and can be retried. Anonymous chat is unaffected.
- What happens when sign-in is degraded or unavailable? Signed-in features show a friendly message, and anonymous chat keeps working as in v1.
- What happens when a signed-in customer's account is disabled during a conversation? The next request is refused, and the conversation stays stored but is not accessible.
- What happens when an admin tries to disable or demote the last active admin account? The change is refused, so the system always has at least one active admin.
- What happens when an agent is disabled while handling an escalation? Their further actions are refused. The escalation stays available to other agents.
- What happens when someone signs up with an email address that is already registered? The response is the same as for a new address, and the account's owner is notified by email (see Assumptions).
- What happens when a shared agent credential is presented after the migration window closes? It is refused.
- What happens when the customer feature control is turned off? Sign-in, history, and account-linked escalation are hidden from customers. Anonymous chat is unaffected, and saved history is kept but not shown.

## Requirements *(mandatory)*

### Functional Requirements

#### Customer accounts

- **FR-001**: Visitors MUST be able to create a customer account with an email address and a password.
- **FR-002**: A customer's email address MUST be verified before any conversation is saved to that account. Until it is verified, the conversation stays session-only and the customer is told how to verify.
- **FR-003**: Passwords MUST be at least 12 characters long and MUST NOT be on a published list of common passwords.
- **FR-004**: Passwords and other credentials MUST NOT be shown on any screen, returned by any response, or written to any log.
- **FR-005**: Signed-in access MUST expire. The access period is about 15 minutes and is renewed silently during use from a continuation credential valid for about 7 days. Each renewal MUST issue a new continuation credential and retire the one it replaces.
- **FR-006**: Session credentials MUST NOT be readable by page scripts, and MUST NOT be kept in browser storage that persists beyond the page session.
- **FR-007**: Signing out MUST end that session on the server, so its credentials stop working, including on renewal.
- **FR-008**: Customers MUST be able to end all of their sessions at once ("sign out everywhere"), including the current one.
- **FR-009**: Customers MUST be able to request a password reset by email. The reset link MUST be single-use, MUST expire 30 minutes after it is sent, and only the most recently sent link is valid.
- **FR-010**: A successful password reset MUST end every existing session of that account.
- **FR-011**: Password reset requests, sign-in failures, and sign-up attempts MUST NOT reveal whether an email address has an account.
- **FR-012**: Repeated failed sign-in attempts on an account MUST cause increasing delays. After five consecutive failures, the account MUST be locked for 15 minutes.
- **FR-013**: Sign-in, sign-up, and reset requests MUST be rate limited both per source and per account.

#### Chat with optional sign-in

- **FR-014**: Chat used without signing in MUST behave exactly as in v1 (see [001 spec](../001-support-chat-assistant/spec.md)), and MUST keep working when sign-in, account data, or email services are unavailable.
- **FR-015**: A signed-in customer's conversations, messages, and ratings MUST be saved to their account.
- **FR-016**: A signed-in customer MUST be able to see a list of their own past conversations and reopen any of them to review or continue.
- **FR-017**: A user MUST NOT be able to see, change, or rate another user's conversation. A request for another user's conversation MUST receive the same response as a conversation that does not exist.
- **FR-018**: An anonymous conversation in progress MAY be attached to an account by its holder at sign-in. Attaching requires the conversation's identifier and the sign-in in the same action. A conversation can be attached only once.
- **FR-019**: An escalation raised from a signed-in account MUST carry that account's verified identifier and email address. An anonymous escalation MUST carry no account identity.
- **FR-020**: A signed-in customer MUST be able to delete their chat history. Deletion MUST remove their conversations, messages, and ratings.
- **FR-021**: A signed-in customer MUST be able to delete their account. Deletion MUST end all their sessions, prevent further sign-in, and remove their conversations, messages, and ratings.

#### Agents and admins

- **FR-022**: Every support agent and admin MUST have an individual account. After the migration window, human access MUST NOT be granted through any shared credential.
- **FR-023**: Agent and admin sign-in MUST require a second verification factor, an authenticator-app one-time code, in addition to the password.
- **FR-024**: Only accounts with the AGENT or ADMIN role MAY use agent functions (view escalations, change status, reply). All other users MUST be refused.
- **FR-025**: An agent MUST see only the escalations assigned to them.
- **FR-026**: An admin MAY see all escalations. An admin MUST NOT have default access to customer conversations that are not part of an escalation (see Assumptions).
- **FR-027**: Each agent action (view escalation, change status, reply) MUST be recorded with the acting account's identifier and the time of the action.
- **FR-028**: An escalation linked to a customer account MUST show the agent the verified email address and account identifier, so the agent does not need to ask who the customer is.
- **FR-029**: Admins MUST be able to create agent accounts, disable accounts, and change an agent or admin account between the AGENT and ADMIN roles.
- **FR-030**: Disabling an account MUST end all its sessions immediately and MUST refuse new sign-in to it.
- **FR-031**: Every account creation, disablement, and role change MUST be recorded with the acting admin's identifier and the time.
- **FR-032**: The last active admin account MUST NOT be disabled or demoted.
- **FR-033**: During the migration window, the shared agent credential and individual agent accounts are both accepted. After the window closes, the shared credential MUST be refused everywhere it is accepted today.
- **FR-034**: Every function MUST enforce access by role and by ownership on the server. Hiding a control in the interface is not sufficient.

#### Operations, privacy, and accessibility

- **FR-035**: Authentication events (sign-in success and failure, lockout, reset requested, session ended, account created, account disabled, role changed) MUST be recorded with the request identifier and the account identifier where known. Records MUST NOT contain passwords, tokens, or email addresses.
- **FR-036**: Authentication event records MUST be kept for a bounded, documented period (see Assumptions).
- **FR-037**: Customer sign-in features MUST be controlled by a feature control. When it is off, anonymous chat is unaffected and saved history is kept but not shown.
- **FR-038**: Verification and reset emails MUST contain only a single-use link. They MUST NOT contain a password or any session credential.
- **FR-039**: Sign-up, sign-in, reset, and password screens MUST be usable with a keyboard and a screen reader, and their error messages MUST NOT disclose whether an account exists.

### Access Matrix

| Resource | Anonymous | Customer | Agent | Admin |
|---|---|---|---|---|
| Chat in own conversation | Yes | Yes | Yes | Yes |
| List own past conversations | No | Own only | No | No (see Assumptions) |
| Attach anonymous conversation at sign-in | Holder only, at sign-in | Holder only, at sign-in | No | No |
| Request a human agent | Yes | Yes | n/a | n/a |
| Agent escalation functions (view, status, reply) | No | No | Assigned only | Yes |
| Manage agent accounts | No | No | No | Yes |

### Key Entities *(include if feature involves data)*

- **User Account**: A person who can sign in. Holds an email address, verified status, a password credential stored only in protected form, a role (CUSTOMER, AGENT, or ADMIN), an active or disabled status, and a creation time. Agent and admin accounts also hold second-factor enrollment.
- **Sign-In Session**: One signed-in period on one device, belonging to one User Account. Has a start, an expiry, and a status. It can be ended individually or together with all the account's other sessions.
- **Password Reset Request**: A single-use, 30-minute request tied to a User Account. Only the most recent one is valid.
- **Email Verification Request**: A single-use request tied to a User Account that confirms the email address.
- **Chat Session**: The existing conversation. It gains an optional owner account. It can be attached to an account once, at sign-in.
- **Escalation Request**: The existing escalation. It gains an optional verified account identity and an assigned agent. Each agent action on it is recorded.
- **Audit Record**: An entry for an authentication event or a privileged action. Holds the acting account (if known), the event type, the time, and the request identifier. It never holds credentials or email addresses.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: At least 40% of returning customers sign in within the first month after customer launch.
- **SC-002**: Fewer than 1% of sign-in attempts fail for any reason other than wrong credentials.
- **SC-003**: 100% of agent actions made after the migration window are recorded against an individual account.
- **SC-004**: In acceptance testing, 100% of cross-user access attempts (conversations, escalations, accounts) receive the same response as for a nonexistent item, and no data is disclosed.
- **SC-005**: In acceptance testing, every access rule in the Access Matrix is verified, and none fails.
- **SC-006**: During a simulated outage of sign-in and email, 100% of anonymous chats receive replies as they do in v1.
- **SC-007**: Sign-in completes in under 500 milliseconds for 95% of attempts under normal load.
- **SC-008**: Checking a signed-in request adds under 5 milliseconds to the response time for 95% of requests under normal load.
- **SC-009**: 100% of password reset requests show the same visible response whether or not the email address is registered.
- **SC-010**: In a log review, zero passwords, tokens, or email addresses appear in any log entry.
- **SC-011**: After the migration window closes, zero requests succeed using the shared agent credential.
- **SC-012**: Median escalation handling time is lower than the v1 baseline, because agents no longer have to ask who the customer is.

## Assumptions

- **Identity store**: Customer and agent accounts are held in a new user store for the chat service, not in Caramel AI's main product accounts. This follows the new tables listed in PRD 002 section 7. Whether to use Caramel AI's existing account system (which would favor OIDC) is an open decision, to be confirmed before planning. It affects FR-001 to FR-006 and the Key Entities.
- **Customer sign-in method**: Customers sign in with email and password at launch, as PRD 002 section 4 requires. Magic-link or passwordless sign-in is not included. Whether to offer it first is an open decision.
- **Agent and admin second factor**: Authenticator-app verification is required for agents and admins at launch, per PRD 002 FR-15, which is pending confirmation. Customers have no second factor, because customer MFA is out of scope.
- **Agent visibility**: Agents see only escalations assigned to them, following the PRD 002 access matrix. This replaces the agent-wide visibility in 001 FR-009 for agents. How escalations are assigned and reassigned is defined during planning. Confirm this in clarification.
- **Admin access to conversations**: PRD 002's access matrix gives admins access to all past sessions. That conflicts with Principle VI ("a user MUST NOT read another user's data"), so this spec grants admins no default access to customer conversations outside escalations (FR-026). Granting that access would need an explicit exception through a constitution amendment. This is the most important decision to confirm.
- **Lockout values**: Five consecutive failed sign-ins lead to a 15-minute lockout, with increasing delay before the lockout. The PRD does not set numbers. Rate-limit ceilings are set during planning.
- **Verification link lifetime**: An email verification link expires after 24 hours. The PRD does not set this.
- **Sign-up for a registered address**: The sign-up response is the same as for a new address, and the existing owner receives an email notice. Planning confirms the exact wording.
- **Unverified accounts**: An unverified account can sign in and chat, but its conversations stay session-only until verified (FR-002).
- **Chat history retention**: Saved history for signed-in customers is kept until the customer deletes it. No automatic expiry is set. PRD 002 open questions list retention as unresolved.
- **Escalations on account deletion**: An escalation an agent has already received keeps its transcript, and the customer's identity fields are removed when the account is deleted. The PRD's deletion list does not cover escalations. Confirm this.
- **Authentication log retention**: Authentication event records are kept for a bounded period. This spec assumes 90 days as a default, to be confirmed against Caramel AI's data policy.
- **Compliance**: No GDPR or SOC 2 requirement has been confirmed. This spec uses the defaults in Principle V and the privacy requirements above. If a requirement applies, it changes FR-036 and the retention assumptions.
- **Email provider**: No transactional email provider is chosen. Credentials for it are kept in environment configuration, not in code or the browser (constitution Principle II). A provider must be approved before customer launch.
- **Token lifetimes**: Access about 15 minutes, continuation about 7 days, and reset link 30 minutes, as PRD 002 sets them. Reuse of a replaced continuation credential ends all of the account's sessions (see Edge Cases). This is a standard protection that is assumed here.
- **Rollout**: Phase 1 introduces agent accounts while accepting the shared credential. Phase 2 releases customer sign-in, history, and claiming behind the customer feature control (FR-037). Phase 3 removes the shared credential (FR-033). Each phase is acceptance-tested at its boundary.
- **Supersession**: This spec supersedes 001 FR-017 and the out-of-scope line that excludes personalization beyond a session or user ID. The constitution v2.0.0 lifts that limit for identity linking only. Account details are not used to personalize answers in this spec, and answers stay grounded in support content (Principle I).
- **Out of scope**: Social or enterprise SSO (Google, GitHub, SAML); customer multi-factor authentication; account-changing or billing actions from chat; migration of Caramel AI's main product accounts; using account details to personalize answers; languages other than English.
