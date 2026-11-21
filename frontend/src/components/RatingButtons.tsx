import type { RatingValue } from '../services/types'

interface Props {
  value?: RatingValue
  onRate: (value: RatingValue) => void
  disabled?: boolean
}

export function RatingButtons({ value, onRate, disabled = false }: Props) {
  return (
    <div className="rating" role="group" aria-label="Rate this response">
      <button
        type="button"
        aria-label="Helpful"
        aria-pressed={value === 'UP'}
        disabled={disabled}
        onClick={() => onRate('UP')}
      >
        👍
      </button>
      <button
        type="button"
        aria-label="Not helpful"
        aria-pressed={value === 'DOWN'}
        disabled={disabled}
        onClick={() => onRate('DOWN')}
      >
        👎
      </button>
    </div>
  )
}
