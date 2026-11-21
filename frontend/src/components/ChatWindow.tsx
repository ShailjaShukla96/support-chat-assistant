import { useChat } from '../hooks/useChat'
import { Composer } from './Composer'
import { EscalationButton } from './EscalationButton'
import { MessageList } from './MessageList'

export function ChatWindow() {
  const { messages, pending, escalating, send, escalate, rate, startNewChat } = useChat()

  return (
    <section className="chat-window" aria-label="Caramel AI support chat">
      <header>
        <h1>Caramel AI Support</h1>
      </header>
      <div className="chat-toolbar">
        <EscalationButton onClick={escalate} disabled={escalating} />
        <button type="button" onClick={startNewChat}>
          New chat
        </button>
      </div>
      <MessageList
        messages={messages}
        pending={pending}
        onEscalate={escalate}
        escalating={escalating}
        onRate={rate}
      />
      <Composer onSend={send} disabled={pending} />
    </section>
  )
}
