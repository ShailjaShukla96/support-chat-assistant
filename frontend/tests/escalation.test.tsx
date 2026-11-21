import { describe, expect, it } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { server } from './mocks/server'
import { SESSION_ID } from './mocks/handlers'
import { ChatWindow } from '../src/components/ChatWindow'

const MESSAGES_URL = `/api/sessions/${SESSION_ID}/messages`
const ESCALATIONS_URL = `/api/sessions/${SESSION_ID}/escalations`
const RECEIPT = {
  escalationId: 'e-1',
  receivedAt: '2026-11-14T10:00:00Z',
  message: 'Your request for a human agent has been recorded.',
}

function noAnswer() {
  return HttpResponse.json({
    messageId: 'm-noanswer',
    text: 'I do not know the answer to that. I can connect you with a human agent.',
    outcome: 'NO_ANSWER',
    escalationOffered: true,
  })
}

describe('escalation (User Story 2)', () => {
  it('shows a Talk to a human button at all times, even before any message', () => {
    render(<ChatWindow />)
    expect(screen.getByRole('button', { name: /talk to a human/i })).toBeEnabled()
  })

  it('clicking the button records an escalation and shows the confirmation', async () => {
    const user = userEvent.setup()
    let calls = 0
    server.use(
      http.post(ESCALATIONS_URL, () => {
        calls += 1
        return HttpResponse.json(RECEIPT, { status: 201 })
      }),
    )
    render(<ChatWindow />)

    await user.click(screen.getByRole('button', { name: /talk to a human/i }))

    expect(await screen.findByText(RECEIPT.message)).toBeInTheDocument()
    expect(calls).toBe(1)
  })

  it('disables the button while the request is in flight', async () => {
    const user = userEvent.setup()
    let release: () => void = () => {}
    const gate = new Promise<void>((resolve) => (release = resolve))
    server.use(
      http.post(ESCALATIONS_URL, async () => {
        await gate
        return HttpResponse.json(RECEIPT, { status: 201 })
      }),
    )
    render(<ChatWindow />)

    await user.click(screen.getByRole('button', { name: /talk to a human/i }))
    expect(screen.getByRole('button', { name: /talk to a human/i })).toBeDisabled()

    release()
    await screen.findByText(RECEIPT.message)
    await waitFor(() => expect(screen.getByRole('button', { name: /talk to a human/i })).toBeEnabled())
  })

  it('an assistant reply that offers escalation shows an action under that message', async () => {
    const user = userEvent.setup()
    server.use(
      http.post(MESSAGES_URL, () => noAnswer()),
      http.post(ESCALATIONS_URL, () => HttpResponse.json(RECEIPT, { status: 201 })),
    )
    render(<ChatWindow />)

    await user.type(screen.getByRole('textbox', { name: /message/i }), 'Do you offer a quantum API?{Enter}')
    const action = await screen.findByRole('button', { name: /connect me with a human agent/i })
    await user.click(action)

    expect(await screen.findByText(RECEIPT.message)).toBeInTheDocument()
    // the offer is consumed once the customer has escalated
    await waitFor(() =>
      expect(screen.queryByRole('button', { name: /connect me with a human agent/i })).not.toBeInTheDocument(),
    )
  })

  it('does not show the inline action when escalation is not offered', async () => {
    const user = userEvent.setup()
    server.use(
      http.post(MESSAGES_URL, () =>
        HttpResponse.json({ messageId: 'm1', text: 'A 429 means the rate limit.', outcome: 'ANSWERED', escalationOffered: false }),
      ),
    )
    render(<ChatWindow />)

    await user.type(screen.getByRole('textbox', { name: /message/i }), 'Why 429?{Enter}')
    await screen.findByText('A 429 means the rate limit.')

    expect(screen.queryByRole('button', { name: /connect me with a human agent/i })).not.toBeInTheDocument()
  })

  it('shows an ESCALATED reply as a normal assistant message without an extra action', async () => {
    const user = userEvent.setup()
    server.use(
      http.post(MESSAGES_URL, () =>
        HttpResponse.json({
          messageId: 'm2',
          text: RECEIPT.message,
          outcome: 'ESCALATED',
          escalationOffered: false,
          escalationId: 'e-9',
        }),
      ),
    )
    render(<ChatWindow />)

    await user.type(screen.getByRole('textbox', { name: /message/i }), 'I want to talk to a person{Enter}')

    expect(await screen.findByText(RECEIPT.message)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /connect me with a human agent/i })).not.toBeInTheDocument()
  })

  it('shows a friendly retry message when the escalation request fails', async () => {
    const user = userEvent.setup()
    server.use(http.post(ESCALATIONS_URL, () => HttpResponse.error()))
    render(<ChatWindow />)

    await user.click(screen.getByRole('button', { name: /talk to a human/i }))

    expect(await screen.findByText(/couldn.t send your request/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /talk to a human/i })).toBeEnabled()
  })
})
