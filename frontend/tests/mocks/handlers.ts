import { http, HttpResponse } from 'msw'

// Default handlers; individual tests override with server.use(...)
export const SESSION_ID = '11111111-1111-4111-8111-111111111111'

export const handlers = [
  http.post('/api/sessions', () =>
    HttpResponse.json({ sessionId: SESSION_ID, createdAt: '2026-11-14T10:00:00Z' }, { status: 201 }),
  ),
]
