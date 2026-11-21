import { useCallback, useRef, useState } from 'react'
import { ApiError, createSession, rateMessage, requestEscalation, sendMessage } from '../services/api'
import type { Outcome, RatingValue } from '../services/types'

export interface ChatMessage {
  id: string
  role: 'user' | 'assistant'
  text: string
  outcome?: Outcome
  escalationOffered?: boolean
  /** True for client-generated error notices (not from the model). */
  isError?: boolean
  /** True only for replies stored by the server, which have a real message ID that can be rated. */
  rateable?: boolean
  rating?: RatingValue
}

export const FRIENDLY_ERROR =
  'Something went wrong on our side. Please try again in a moment, or ask for a human agent.'
const INPUT_ERROR = 'Please check your message and try again.'
export const ESCALATION_FAILED = "We couldn't send your request for a human agent. Please try again."

/** Once the customer has escalated, earlier "connect me" offers are no longer needed. */
function consumeOffers(messages: ChatMessage[]): ChatMessage[] {
  return messages.map((m) => (m.escalationOffered ? { ...m, escalationOffered: false } : m))
}

let counter = 0
const localId = () => `local-${++counter}`

export function useChat() {
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [pending, setPending] = useState(false)
  const [escalating, setEscalating] = useState(false)
  // The session is created lazily on first use, so opening the widget never creates empty sessions.
  const sessionRef = useRef<Promise<string> | null>(null)
  // Bumped by startNewChat so a late reply from the previous chat is dropped instead of shown in the new one.
  const generationRef = useRef(0)

  const ensureSession = useCallback((): Promise<string> => {
    if (!sessionRef.current) {
      const p = createSession().then((s) => s.sessionId)
      p.catch(() => {
        if (sessionRef.current === p) sessionRef.current = null // allow a retry
      })
      sessionRef.current = p
    }
    return sessionRef.current
  }, [])

  const append = useCallback((m: ChatMessage) => setMessages((prev) => [...prev, m]), [])

  const send = useCallback(
    async (text: string) => {
      const generation = generationRef.current
      append({ id: localId(), role: 'user', text })
      setPending(true)
      try {
        const sessionId = await ensureSession()
        const reply = await sendMessage(sessionId, text)
        if (generation !== generationRef.current) return
        const next: ChatMessage = {
          id: reply.messageId,
          role: 'assistant',
          text: reply.text,
          outcome: reply.outcome,
          escalationOffered: reply.escalationOffered,
          rateable: true,
        }
        setMessages((prev) => [...(reply.outcome === 'ESCALATED' ? consumeOffers(prev) : prev), next])
      } catch (e) {
        if (generation !== generationRef.current) return
        const text = e instanceof ApiError && e.status === 400 ? INPUT_ERROR : FRIENDLY_ERROR
        append({ id: localId(), role: 'assistant', text, isError: true, escalationOffered: true })
      } finally {
        if (generation === generationRef.current) setPending(false)
      }
    },
    [append, ensureSession],
  )

  const escalate = useCallback(async () => {
    const generation = generationRef.current
    setEscalating(true)
    try {
      const sessionId = await ensureSession()
      const receipt = await requestEscalation(sessionId)
      if (generation !== generationRef.current) return
      setMessages((prev) => [
        ...consumeOffers(prev),
        { id: localId(), role: 'assistant', text: receipt.message, outcome: 'ESCALATED' },
      ])
    } catch {
      if (generation !== generationRef.current) return
      append({ id: localId(), role: 'assistant', text: ESCALATION_FAILED, isError: true })
    } finally {
      if (generation === generationRef.current) setEscalating(false)
    }
  }, [append, ensureSession])

  /** Stores the rating on the server first; the selected state changes only when that succeeds. */
  const rate = useCallback(async (messageId: string, value: RatingValue) => {
    const generation = generationRef.current
    try {
      const saved = await rateMessage(messageId, value)
      if (generation !== generationRef.current) return
      setMessages((prev) => prev.map((m) => (m.id === messageId ? { ...m, rating: saved.value } : m)))
    } catch {
      // keep the previous state; a failed rating is not worth interrupting the conversation
    }
  }, [])

  /** Starts a fresh conversation: new session on next use, nothing carried over (FR-004). */
  const startNewChat = useCallback(() => {
    generationRef.current += 1
    sessionRef.current = null
    setMessages([])
    setPending(false)
    setEscalating(false)
  }, [])

  return { messages, pending, escalating, send, escalate, rate, startNewChat }
}
