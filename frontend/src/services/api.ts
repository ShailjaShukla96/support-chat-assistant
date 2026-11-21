import type {
  AssistantReply,
  EscalationReason,
  EscalationReceipt,
  Rating,
  RatingValue,
  Session,
} from './types'

// Non-secret. Empty means same-origin (the Vite dev server proxies /api to the backend).
const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? ''

/** Carries only the problem title, never raw server detail, so nothing technical reaches the customer. */
export class ApiError extends Error {
  readonly status: number

  constructor(title: string, status: number) {
    super(title)
    this.name = 'ApiError'
    this.status = status
  }
}

async function request<T>(path: string, init: RequestInit): Promise<T> {
  let response: Response
  try {
    response = await fetch(`${BASE_URL}${path}`, {
      ...init,
      headers: { 'Content-Type': 'application/json', ...init.headers },
    })
  } catch {
    throw new ApiError('Network error', 0)
  }
  if (!response.ok) {
    let title = 'Request failed'
    try {
      const body = (await response.json()) as { title?: string }
      if (body.title) title = body.title
    } catch {
      // keep the generic title
    }
    throw new ApiError(title, response.status)
  }
  return (await response.json()) as T
}

export function createSession(userRef?: string): Promise<Session> {
  return request<Session>('/api/sessions', {
    method: 'POST',
    body: JSON.stringify(userRef ? { userRef } : {}),
  })
}

export function sendMessage(sessionId: string, text: string): Promise<AssistantReply> {
  return request<AssistantReply>(`/api/sessions/${sessionId}/messages`, {
    method: 'POST',
    body: JSON.stringify({ text }),
  })
}

export function requestEscalation(
  sessionId: string,
  reason: EscalationReason = 'CUSTOMER_REQUESTED',
): Promise<EscalationReceipt> {
  return request<EscalationReceipt>(`/api/sessions/${sessionId}/escalations`, {
    method: 'POST',
    body: JSON.stringify({ reason }),
  })
}

export function rateMessage(messageId: string, value: RatingValue): Promise<Rating> {
  return request<Rating>(`/api/messages/${messageId}/rating`, {
    method: 'PUT',
    body: JSON.stringify({ value }),
  })
}
