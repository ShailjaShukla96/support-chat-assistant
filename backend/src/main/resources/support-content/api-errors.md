# Common API errors
> SAMPLE CONTENT: replace with approved Caramel AI documentation before pilot

- 400 Bad Request: the request body is malformed or a parameter is invalid. Check the JSON syntax, the `model` name, and that `messages` is a non-empty list.
- 401 Unauthorized: the API key is missing, wrong, or revoked. Send it in the `Authorization: Bearer <key>` header. Never put keys in client-side code.
- 404 Not Found: the endpoint path or model name does not exist. Check for typos.
- 429 Too Many Requests: you hit a rate limit. See the rate limits guide.
- 500 and 503: a temporary problem on the service side. Retry after a short delay with backoff. If it continues for more than a few minutes, contact a human support agent with the request ID.
