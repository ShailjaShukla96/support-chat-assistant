import { describe, expect, it } from 'vitest'
import { render, screen, waitFor, fireEvent } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse, delay } from 'msw'
import { server } from './mocks/server'
import { SESSION_ID } from './mocks/handlers'
import { ChatWindow } from '../src/components/ChatWindow'

const MESSAGES_URL = `/api/sessions/${SESSION_ID}/messages`

function reply(text: string, outcome = 'ANSWERED', escalationOffered = false) {
  return HttpResponse.json({ messageId: crypto.randomUUID(), text, outcome, escalationOffered })
}

describe('chat window (User Story 1)', () => {
  it('blocks an empty or whitespace-only message and sends nothing', async () => {
    const user = userEvent.setup()
    render(<ChatWindow />)

    await user.type(screen.getByRole('textbox', { name: /message/i }), '   ')
    await user.click(screen.getByRole('button', { name: /send/i }))

    expect(screen.getByRole('alert')).toHaveTextContent(/type a question/i)
    // msw is configured with onUnhandledFrame: 'error', so any request would fail this test
    expect(screen.queryByText('   ')).not.toBeInTheDocument()
  })

  it('blocks a message over 2,000 characters with a visible input error', async () => {
    const user = userEvent.setup()
    render(<ChatWindow />)
    const box = screen.getByRole('textbox', { name: /message/i })

    fireEvent.change(box, { target: { value: 'x'.repeat(2001) } })
    await user.click(screen.getByRole('button', { name: /send/i }))

    expect(screen.getByRole('alert')).toHaveTextContent(/2,?000/)
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })

  it('accepts exactly 2,000 characters', async () => {
    const user = userEvent.setup()
    server.use(http.post(MESSAGES_URL, () => reply('ok')))
    render(<ChatWindow />)

    fireEvent.change(screen.getByRole('textbox', { name: /message/i }), { target: { value: 'x'.repeat(2000) } })
    await user.click(screen.getByRole('button', { name: /send/i }))

    expect(await screen.findByText('ok')).toBeInTheDocument()
  })

  it('sends on Enter, shows a typing indicator while waiting, then the reply', async () => {
    const user = userEvent.setup()
    server.use(
      http.post(MESSAGES_URL, async () => {
        await delay(150)
        return reply('A 429 means you hit the rate limit.')
      }),
    )
    render(<ChatWindow />)

    await user.type(screen.getByRole('textbox', { name: /message/i }), 'Why do I get 429?{Enter}')

    expect(screen.getByText('Why do I get 429?')).toBeInTheDocument()
    expect(await screen.findByRole('status')).toHaveTextContent(/typing/i)
    expect(await screen.findByText('A 429 means you hit the rate limit.')).toBeInTheDocument()
    await waitFor(() => expect(screen.queryByRole('status')).not.toBeInTheDocument())
    expect(screen.getByRole('textbox', { name: /message/i })).toHaveValue('')
  })

  it('Shift+Enter inserts a newline instead of sending', async () => {
    const user = userEvent.setup()
    render(<ChatWindow />)
    const box = screen.getByRole('textbox', { name: /message/i })

    await user.type(box, 'line one{Shift>}{Enter}{/Shift}line two')

    expect(box).toHaveValue('line one\nline two')
  })

  it('shows the friendly text for an UNAVAILABLE outcome and no raw error text', async () => {
    const user = userEvent.setup()
    server.use(
      http.post(MESSAGES_URL, () =>
        reply('I cannot answer right now. You can try again in a moment.', 'UNAVAILABLE', true),
      ),
    )
    render(<ChatWindow />)

    await user.type(screen.getByRole('textbox', { name: /message/i }), 'hello{Enter}')

    expect(await screen.findByText(/cannot answer right now/i)).toBeInTheDocument()
    expect(screen.queryByText(/exception|stack|500|jdbc/i)).not.toBeInTheDocument()
  })

  it('turns a server error or network failure into a friendly message without technical detail', async () => {
    const user = userEvent.setup()
    server.use(
      http.post(MESSAGES_URL, () =>
        HttpResponse.json(
          { title: 'Server error', status: 500, detail: 'NullPointerException at com.caramelai' },
          { status: 500 },
        ),
      ),
    )
    render(<ChatWindow />)

    await user.type(screen.getByRole('textbox', { name: /message/i }), 'hello{Enter}')

    expect(await screen.findByText(/something went wrong|try again/i)).toBeInTheDocument()
    expect(screen.queryByText(/NullPointer|caramelai|500/)).not.toBeInTheDocument()

    server.use(http.post(MESSAGES_URL, () => HttpResponse.error()))
    await user.type(screen.getByRole('textbox', { name: /message/i }), 'again{Enter}')
    await waitFor(() => expect(screen.getAllByText(/something went wrong|try again/i).length).toBeGreaterThan(1))
  })
})
