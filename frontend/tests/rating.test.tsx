import { describe, expect, it } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { server } from './mocks/server'
import { SESSION_ID } from './mocks/handlers'
import { ChatWindow } from '../src/components/ChatWindow'

const MESSAGES_URL = `/api/sessions/${SESSION_ID}/messages`

function answer(id: string, text: string) {
  return HttpResponse.json({ messageId: id, text, outcome: 'ANSWERED', escalationOffered: false })
}

describe('rating (User Story 4)', () => {
  it('shows rating buttons only on assistant answers, not on customer messages or error notices', async () => {
    const user = userEvent.setup()
    server.use(http.post(MESSAGES_URL, () => answer('m1', 'A 429 means the rate limit.')))
    render(<ChatWindow />)

    await user.type(screen.getByRole('textbox', { name: /message/i }), 'Why 429?{Enter}')
    await screen.findByText('A 429 means the rate limit.')

    expect(screen.getAllByRole('button', { name: /^helpful$/i })).toHaveLength(1)
    expect(screen.getAllByRole('button', { name: /^not helpful$/i })).toHaveLength(1)

    server.use(http.post(MESSAGES_URL, () => HttpResponse.error()))
    await user.type(screen.getByRole('textbox', { name: /message/i }), 'again{Enter}')
    await screen.findByText(/something went wrong/i)

    // still exactly one pair: the customer message and the error notice have none
    expect(screen.getAllByRole('button', { name: /^helpful$/i })).toHaveLength(1)
  })

  it('sends UP then DOWN and shows the selected state', async () => {
    const user = userEvent.setup()
    const sent: string[] = []
    server.use(
      http.post(MESSAGES_URL, () => answer('m1', 'A 429 means the rate limit.')),
      http.put('/api/messages/m1/rating', async ({ request }) => {
        const body = (await request.json()) as { value: string }
        sent.push(body.value)
        return HttpResponse.json({ messageId: 'm1', value: body.value, updatedAt: '2026-11-14T10:00:00Z' })
      }),
    )
    render(<ChatWindow />)
    await user.type(screen.getByRole('textbox', { name: /message/i }), 'Why 429?{Enter}')
    await screen.findByText('A 429 means the rate limit.')

    const up = screen.getByRole('button', { name: /^helpful$/i })
    const down = screen.getByRole('button', { name: /^not helpful$/i })
    expect(up).toHaveAttribute('aria-pressed', 'false')

    await user.click(up)
    await waitFor(() => expect(up).toHaveAttribute('aria-pressed', 'true'))
    expect(down).toHaveAttribute('aria-pressed', 'false')

    await user.click(down)
    await waitFor(() => expect(down).toHaveAttribute('aria-pressed', 'true'))
    expect(up).toHaveAttribute('aria-pressed', 'false')
    expect(sent).toEqual(['UP', 'DOWN'])
  })

  it('rates each message independently', async () => {
    const user = userEvent.setup()
    let n = 0
    server.use(
      http.post(MESSAGES_URL, () => answer(`m${++n}`, `answer ${n}`)),
      http.put('/api/messages/:id/rating', async ({ params, request }) => {
        const body = (await request.json()) as { value: string }
        return HttpResponse.json({ messageId: params.id, value: body.value, updatedAt: '2026-11-14T10:00:00Z' })
      }),
    )
    render(<ChatWindow />)
    await user.type(screen.getByRole('textbox', { name: /message/i }), 'one{Enter}')
    await screen.findByText('answer 1')
    await user.type(screen.getByRole('textbox', { name: /message/i }), 'two{Enter}')
    await screen.findByText('answer 2')

    const [firstUp, secondUp] = screen.getAllByRole('button', { name: /^helpful$/i })
    const [, secondDown] = screen.getAllByRole('button', { name: /^not helpful$/i })
    await user.click(firstUp)
    await user.click(secondDown)

    await waitFor(() => expect(firstUp).toHaveAttribute('aria-pressed', 'true'))
    await waitFor(() => expect(secondDown).toHaveAttribute('aria-pressed', 'true'))
    expect(secondUp).toHaveAttribute('aria-pressed', 'false')
  })

  it('keeps the previous state when the rating request fails', async () => {
    const user = userEvent.setup()
    server.use(
      http.post(MESSAGES_URL, () => answer('m1', 'answer')),
      http.put('/api/messages/m1/rating', () => HttpResponse.error()),
    )
    render(<ChatWindow />)
    await user.type(screen.getByRole('textbox', { name: /message/i }), 'q{Enter}')
    await screen.findByText('answer')

    const up = screen.getByRole('button', { name: /^helpful$/i })
    await user.click(up)

    await waitFor(() => expect(up).toBeEnabled())
    expect(up).toHaveAttribute('aria-pressed', 'false')
  })
})
