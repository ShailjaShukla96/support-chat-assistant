# Rate limits and HTTP 429 errors
> SAMPLE CONTENT: replace with approved Caramel AI documentation before pilot

An HTTP 429 response means your requests exceeded the rate limit for your plan or for the model you called.

What to do:
- Read the `Retry-After` response header and wait that long before retrying.
- Retry with exponential backoff and a little random jitter instead of retrying immediately.
- Spread traffic evenly instead of sending bursts.
- Check whether the limit applies to requests per minute or to tokens per minute, because large prompts can hit the token limit first.
- If you regularly need higher limits, ask a human support agent about plan options.
