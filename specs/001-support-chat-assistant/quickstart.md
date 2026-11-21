# Quickstart & Validation Guide: Caramel AI Tech Support Chat Assistant

**Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md) | **Contract**: [contracts/chat-api.yaml](contracts/chat-api.yaml) |
**Data model**: [data-model.md](data-model.md)

Written for: engineers running and validating v1. This is a run and validation guide, not an
implementation guide. It assumes the layout in the plan (`backend/`, `frontend/`) exists. Commands are the
intended interface and are created by the tasks.

## Prerequisites

- JDK 21, Maven 3.9+, Node 22+ and npm
- A Google AI Studio API key (only needed for steps 3 and 6)

## 1. Configure secrets

```bash
cp .env.example .env          # .env.example holds placeholders only
# edit .env and set:
#   GOOGLE_AI_STUDIO_API_KEY=<your key>
#   AGENT_API_TOKEN=<any long random string>
```

Expected: `.env` is listed in `.gitignore`; `.env.example` contains no real values.

## 2. Run the default test suites (no network, no key)

```bash
cd backend  && mvn test        # model is stubbed; must pass offline
cd frontend && npm install && npm test
```

Expected: both suites pass without `GOOGLE_AI_STUDIO_API_KEY` set.

## 3. Run the live suite (real model, needs key)

```bash
cd backend && mvn verify -Plive
```

Expected results, matching the spec:

| Check | Pass condition | Spec link |
|-------|----------------|-----------|
| S1 system-instruction support | A rule given only in the system message is followed | R3 |
| S2 streaming support | Recorded yes/no in `research.md` | R5 |
| S3 marker compliance | At least 9 of 10 out-of-knowledge prompts reply with the no-answer outcome | SC-006 |
| Extraction attempts | None of the curated prompts leaks the key text, the canary string, or system-prompt wording | SC-007, FR-011 |
| Non-English prompt | English reply stating English-only support and an escalation offer | FR-016 |
| Billing-change request | Reply states it cannot change accounts or billing and offers escalation | FR-010 |

Record spike results in `research.md` under "Spike results".

## 4. Start the app

```bash
cd backend  && mvn spring-boot:run     # loads .env, serves on :8080
cd frontend && npm run dev             # serves on :5173, proxies /api to :8080
```

Open http://localhost:5173.

## 5. Manual validation scenarios

Run each in the browser. IDs refer to spec stories and requirements.

1. **Grounded answer (Story 1)**: Ask "Why do I get HTTP 429?" Expect a reply based on the sample support
   content, shown in the chat window within a few seconds.
2. **Follow-up context (Story 3)**: After a reply about an error, ask "How do I fix that?" Expect the reply
   to address the same error. Click "New chat" and ask the same follow-up. Expect no knowledge of the
   earlier conversation.
3. **Unknown topic (Story 2, FR-007)**: Ask something absent from the support content (for example about a
   product Caramel AI does not offer). Expect a "do not know" reply and a visible escalation action.
4. **Human request, button (FR-008)**: Click "Talk to a human" mid-conversation. Expect a confirmation
   message.
5. **Human request, free text (FR-008)**: Type "I want to talk to a person". Expect a confirmation without a
   model-generated answer.
6. **Agent view (FR-009)**: Run
   ```bash
   curl -s -H "X-Agent-Token: $AGENT_API_TOKEN" localhost:8080/api/agent/escalations
   ```
   Expect the two escalations from scenarios 4 and 5. Fetch one by ID and confirm the transcript contains
   the earlier messages. Repeat without the header and expect `401`.
7. **Rating (Story 4)**: Click thumbs-up on one reply and thumbs-down on another; change one rating. Check
   the H2 console or a query that each message holds only its latest rating.
8. **Provider outage (FR-012, SC-008)**: Stop the app, set `GOOGLE_AI_STUDIO_API_KEY=invalid`, restart, and
   send a message. Expect a friendly message and an escalation action, with no stack trace, key, or provider
   error text in the UI or the response body.
9. **Validation (edge cases)**: Send a 2,001-character message and a blank message. Expect no assistant
   call and a clear input error.
10. **Billing change (FR-010)**: Ask "Refund my last invoice." Expect a statement that account and billing
    changes are not possible and an escalation offer.

## 6. Security and observability checks

```bash
# 1. The key must not be in the production bundle
cd frontend && npm run build && grep -r "GOOGLE_AI_STUDIO_API_KEY" dist || echo "bundle clean"

# 2. The key must not be in logs
cd backend && mvn spring-boot:run > /tmp/app.log 2>&1   # send a few messages, then stop
grep -c "$GOOGLE_AI_STUDIO_API_KEY" /tmp/app.log         # expect 0
```

Also confirm in `/tmp/app.log`: each chat request has one structured event with request ID, outcome,
latency, and token counts, and no message text.

## 7. Pre-pilot owner checklist

- [ ] Replace the sample content in `backend/src/main/resources/support-content/` with approved Caramel AI
      FAQs, docs, and troubleshooting guides (no customer data).
- [ ] Choose the escalation channel and implement an `EscalationNotifier` for it (v1 only logs).
- [ ] Record the API key's rate and quota limits (spike S4) and size the pilot against them.
- [ ] Confirm the data retention period and add a purge job if the policy requires one.
- [ ] Set the allowed web origin for production CORS.
- [ ] Rotate the pilot API key and agent token after demos.

## Expected metrics after the pilot

Run the derived queries in [data-model.md](data-model.md) to compare against SC-001 (60% resolved
without escalation), SC-002 (70% helpful), and SC-003 (median under 3 seconds).
