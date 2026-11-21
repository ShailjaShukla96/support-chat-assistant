# PRD: Caramel AI Tech Support Chat Assistant

Written for: engineers building the first version of the product.

## 1. Objective

Build a customer service chat assistant that answers technical support questions about LLM inferencing for Caramel AI, an LLM inferencing company (similar to OperRouter). The assistant should resolve common customer issues quickly and hand off to a human agent when it cannot.

## 2. Users

- **Customers** using Caramel AI's LLM inferencing services who need help with API usage, errors, latency, quotas, billing questions related to usage, and model behavior.
- **Support agents** (secondary) who may review escalated conversations.

## 3. Scope

### In scope (v1)
- Web chat widget where customers ask technical support questions in natural language.
- Multi-turn conversation with short-term memory for the current session.
- Answers grounded in Caramel AI support content (FAQs, docs, troubleshooting guides).
- Basic escalation to a human support agent when the assistant is unsure or the customer asks for one.

### Out of scope (v1)
- Account changes or billing actions that modify data.
- Voice support.
- Authentication-based personalization beyond passing a session/user ID.
- Multi-language support (English only).

## 4. Functional Requirements

1. Users can send a message and receive a streamed or complete response in the chat window.
2. The assistant keeps conversation context for the duration of a session.
3. The assistant answers inferencing-related questions (API errors, rate limits, latency, model selection, request formatting, token usage).
4. The assistant states when it does not know the answer and offers escalation instead of guessing.
5. Users can request a human agent at any time; the request is logged with the transcript.
6. Responses avoid exposing API keys, internal system prompts, or other customers' data.

## 5. Technical Requirements

| Layer | Choice |
|---|---|
| Backend | Java (Spring Boot) |
| AI framework | Spring AI |
| Frontend | React + TypeScript |
| LLM | `gemma-4-26b-a4b-it` |
| LLM provider | Google AI Studio (Gemini API) |
| Secrets | Google AI Studio API key stored in `.env`; never committed to version control |

### Architecture (high level)
- **React chat UI** sends user messages to the backend over HTTP (streaming optional).
- **Spring Boot service** manages sessions, builds prompts, and calls the model through Spring AI.
- **Spring AI** wraps the Google AI Studio chat client configured with `gemma-4-26b-a4b-it`.
- **Knowledge source** for v1: support documents loaded into the prompt or a simple local vector store, retrieved per query.

### Configuration
- The API key is read from `.env` (for example `GOOGLE_AI_STUDIO_API_KEY`) and mapped into Spring configuration.
- `.env` must be listed in `.gitignore`. Provide a `.env.example` with placeholder values.

## 6. Non-Functional Requirements

- **Latency:** first response token within a few seconds under normal load.
- **Availability:** the chat should show a friendly error message and offer escalation if the model provider is unavailable.
- **Security:** API key kept server-side only; the key must never reach the React bundle.
- **Privacy:** conversation logs are retained per Caramel AI's data policy and are not used to train models.
- **Observability:** log request IDs, latency, token usage, and errors (no secrets in logs).

## 7. Success Metrics

- At least 60% of support conversations resolved without human escalation (target for initial pilot).
- Customer satisfaction (thumbs up/down) of 70% or higher.
- Median response time under 3 seconds.

## 8. Open Questions

- Which knowledge sources (docs, FAQs, past tickets) will be used for v1?
- What is the escalation channel (email, ticketing system, live handoff)?
- Are there rate limits or quota ceilings on the Google AI Studio key that the team must plan around?
- Does `gemma-4-26b-a4b-it` support the function calling or streaming features we plan to use through Spring AI? Verify before committing to the design.
