interface Props {
  onClick: () => void
  disabled?: boolean
}

/** Always visible: customers can ask for a human at any point (Constitution III, FR-008). */
export function EscalationButton({ onClick, disabled = false }: Props) {
  return (
    <button type="button" className="escalation-button" onClick={onClick} disabled={disabled}>
      Talk to a human
    </button>
  )
}
