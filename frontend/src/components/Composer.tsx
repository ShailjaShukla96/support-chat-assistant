import { useState } from 'react'
import type { FormEvent, KeyboardEvent } from 'react'

export const MAX_MESSAGE_CHARS = 2000

interface Props {
  onSend: (text: string) => void
  disabled?: boolean
}

export function Composer({ onSend, disabled = false }: Props) {
  const [value, setValue] = useState('')
  const [error, setError] = useState<string | null>(null)

  function submit() {
    const text = value.trim()
    if (text.length === 0) {
      setError('Please type a question.')
      return
    }
    if (text.length > MAX_MESSAGE_CHARS) {
      setError(`Your message is too long (maximum 2,000 characters). Please shorten it.`)
      return
    }
    setError(null)
    setValue('')
    onSend(text)
  }

  function onSubmit(e: FormEvent) {
    e.preventDefault()
    submit()
  }

  function onKeyDown(e: KeyboardEvent<HTMLTextAreaElement>) {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      submit()
    }
  }

  return (
    <form className="composer" onSubmit={onSubmit}>
      <textarea
        aria-label="Message"
        placeholder="Ask a question about Caramel AI…"
        rows={2}
        value={value}
        disabled={disabled}
        onChange={(e) => {
          setValue(e.target.value)
          if (error) setError(null)
        }}
        onKeyDown={onKeyDown}
      />
      <button type="submit" disabled={disabled}>
        Send
      </button>
      {error && (
        <p role="alert" className="composer-error">
          {error}
        </p>
      )}
    </form>
  )
}
