import { useEffect, useRef } from 'react'
import type { ChatMessage } from '../hooks/useChat'
import type { RatingValue } from '../services/types'
import { RatingButtons } from './RatingButtons'

interface Props {
  messages: ChatMessage[]
  pending: boolean
  onEscalate?: () => void
  escalating?: boolean
  onRate?: (messageId: string, value: RatingValue) => void
}

export function MessageList({ messages, pending, onEscalate, escalating = false, onRate }: Props) {
  const endRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    endRef.current?.scrollIntoView({ block: 'end' })
  }, [messages, pending])

  return (
    <div className="message-list" aria-live="polite" aria-label="Conversation">
      {messages.length === 0 && !pending && (
        <p className="empty-hint">Ask a technical question about Caramel AI inferencing.</p>
      )}
      {messages.map((m) => (
        <div key={m.id} className={`message message-${m.role}${m.isError ? ' message-error' : ''}`}>
          <p>{m.text}</p>
          {m.rateable && onRate && (
            <RatingButtons value={m.rating} onRate={(value) => onRate(m.id, value)} />
          )}
          {m.escalationOffered && onEscalate && (
            <div className="message-actions">
              <button type="button" onClick={onEscalate} disabled={escalating}>
                Yes, connect me with a human agent
              </button>
            </div>
          )}
        </div>
      ))}
      {pending && (
        <div role="status" className="typing">
          Assistant is typing…
        </div>
      )}
      <div ref={endRef} />
    </div>
  )
}
