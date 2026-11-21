# Reducing latency
> SAMPLE CONTENT: replace with approved Caramel AI documentation before pilot

Response time depends on the model, the prompt length, and the number of output tokens.

Ways to reduce latency:
- Choose a smaller model when the task is simple.
- Keep prompts short. Remove unused context and repeated instructions.
- Set `max_tokens` to the smallest value that fits your use case.
- Use streaming so users see the first words sooner.
- Send requests from a region close to the service when possible.

If latency is much higher than usual, note the request ID and time, then contact a human support agent.
