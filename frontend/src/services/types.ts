// Mirrors specs/001-support-chat-assistant/contracts/chat-api.yaml

export type Outcome = 'ANSWERED' | 'NO_ANSWER' | 'UNAVAILABLE' | 'BLOCKED' | 'ESCALATED'
export type RatingValue = 'UP' | 'DOWN'
export type EscalationReason = 'CUSTOMER_REQUESTED' | 'ASSISTANT_UNSURE' | 'PROVIDER_UNAVAILABLE'

export interface Session {
  sessionId: string
  createdAt: string
}

export interface AssistantReply {
  messageId: string
  text: string
  outcome: Outcome
  escalationOffered: boolean
  escalationId?: string
}

export interface EscalationReceipt {
  escalationId: string
  receivedAt: string
  message: string
}

export interface Rating {
  messageId: string
  value: RatingValue
  updatedAt: string
}
