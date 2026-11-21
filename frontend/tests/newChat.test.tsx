import { describe, expect, it } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { server } from './mocks/server'
import { ChatWindow } from '../src/components/ChatWindow'

describe('new chat (User Story 3)', () => {
  it('clears the conversation and later messages use a new session', async () => {
    const user = userEvent.setup()
    const createdIds: string[] = []
    const postedTo: string[] = []

    server.use(
      http.post('/api/sessions', () => {
        const id = `00000000-0000-4000-8000-00000000000${createdIds.length + 1}`
        createdIds.push(id)
        return HttpResponse.json({ sessionId: id, createdAt: '2026-11-14T10:00:00Z' }, { status: 201 })
      }),
      http.post('/api/sessions/:id/messages', async ({ params, request }) => {
        postedTo.push(String(params.id))
        const body = (await request.json()) as { text: string }
        return HttpResponse.json({
          messageId: crypto.randomUUID(),
          text: `reply to ${body.text}`,
          outcome: 'ANSWERED',
          escalationOffered: false,
        })
      }),
    )
    render(<ChatWindow />)
    const box = () => screen.getByRole('textbox', { name: /message/i })

    await user.type(box(), 'first question{Enter}')
    await screen.findByText('reply to first question')
    await user.type(box(), 'follow up{Enter}')
    await screen.findByText('reply to follow up')
    expect(postedTo).toEqual([createdIds[0], createdIds[0]]) // same session for follow-ups

    await user.click(screen.getByRole('button', { name: /new chat/i }))

    await waitFor(() => expect(screen.queryByText('reply to first question')).not.toBeInTheDocument())
    expect(screen.queryByText('first question')).not.toBeInTheDocument()

    await user.type(box(), 'brand new question{Enter}')
    await screen.findByText('reply to brand new question')
    expect(createdIds).toHaveLength(2)
    expect(postedTo[2]).toBe(createdIds[1])
    expect(postedTo[2]).not.toBe(createdIds[0])
  })

  it('does not apply a late reply from the previous chat to the new one', async () => {
    const user = userEvent.setup()
    let release: () => void = () => {}
    const gate = new Promise<void>((resolve) => (release = resolve))

    server.use(
      http.post('/api/sessions/:id/messages', async () => {
        await gate
        return HttpResponse.json({ messageId: 'late', text: 'late reply', outcome: 'ANSWERED', escalationOffered: false })
      }),
    )
    render(<ChatWindow />)

    await user.type(screen.getByRole('textbox', { name: /message/i }), 'slow question{Enter}')
    await screen.findByRole('status')
    await user.click(screen.getByRole('button', { name: /new chat/i }))
    release()

    await waitFor(() => expect(screen.queryByRole('status')).not.toBeInTheDocument())
    expect(screen.queryByText('late reply')).not.toBeInTheDocument()
  })
})
