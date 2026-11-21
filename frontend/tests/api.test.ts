import { describe, expect, it } from 'vitest'
import { http, HttpResponse } from 'msw'
import { server } from './mocks/server'
import { SESSION_ID } from './mocks/handlers'
import { ApiError, createSession, rateMessage, requestEscalation, sendMessage } from '../src/services/api'

describe('api client', () => {
  it('creates a session', async () => {
    const s = await createSession()
    expect(s.sessionId).toBe(SESSION_ID)
  })

  it('sends a message and returns the reply', async () => {
    server.use(
      http.post(`/api/sessions/${SESSION_ID}/messages`, async ({ request }) => {
        const body = (await request.json()) as { text: string }
        return HttpResponse.json({
          messageId: 'm1',
          text: `echo: ${body.text}`,
          outcome: 'ANSWERED',
          escalationOffered: false,
        })
      }),
    )
    const reply = await sendMessage(SESSION_ID, 'hello')
    expect(reply.text).toBe('echo: hello')
    expect(reply.outcome).toBe('ANSWERED')
  })

  it('throws ApiError carrying only the problem title for non-2xx responses', async () => {
    server.use(
      http.post(`/api/sessions/${SESSION_ID}/messages`, () =>
        HttpResponse.json(
          { title: 'Invalid request', status: 400, detail: 'internal detail jdbc:h2:file' },
          { status: 400 },
        ),
      ),
    )
    const err = await sendMessage(SESSION_ID, '').catch((e: unknown) => e)
    expect(err).toBeInstanceOf(ApiError)
    expect((err as ApiError).message).toBe('Invalid request')
    expect((err as ApiError).message).not.toContain('jdbc')
    expect((err as ApiError).status).toBe(400)
  })

  it('maps a network failure to an ApiError', async () => {
    server.use(http.post(`/api/sessions/${SESSION_ID}/messages`, () => HttpResponse.error()))
    const err = await sendMessage(SESSION_ID, 'hi').catch((e: unknown) => e)
    expect(err).toBeInstanceOf(ApiError)
    expect((err as ApiError).status).toBe(0)
  })

  it('requests escalation and rates messages', async () => {
    server.use(
      http.post(`/api/sessions/${SESSION_ID}/escalations`, () =>
        HttpResponse.json(
          { escalationId: 'e1', receivedAt: '2026-11-14T10:00:00Z', message: 'Received' },
          { status: 201 },
        ),
      ),
      http.put('/api/messages/m1/rating', () =>
        HttpResponse.json({ messageId: 'm1', value: 'UP', updatedAt: '2026-11-14T10:00:00Z' }),
      ),
    )
    expect((await requestEscalation(SESSION_ID)).escalationId).toBe('e1')
    expect((await rateMessage('m1', 'UP')).value).toBe('UP')
  })
})
