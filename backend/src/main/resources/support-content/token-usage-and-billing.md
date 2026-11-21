# Token usage and usage-based billing
> SAMPLE CONTENT: replace with approved Caramel AI documentation before pilot

- Usage is counted in tokens. Both the prompt (input) and the generated reply (output) count.
- Each response includes a `usage` object with prompt tokens, completion tokens, and total tokens.
- Long conversations cost more because the whole history is sent with each request. Trim or summarize old messages.
- Use `max_tokens` to cap the length of replies and avoid surprise usage.
- This assistant cannot change your plan, issue refunds, or modify billing. A human support agent can help with those requests.
