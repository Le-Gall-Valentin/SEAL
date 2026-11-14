import type { ReactNode } from 'react'

interface SecondaryButtonProps {
  readonly onClick: () => void
  readonly children: ReactNode
}

/** Bouton secondaire (« Annuler », « Réessayer »), au style des boutons secondaires de la maquette. */
export function SecondaryButton({ onClick, children }: SecondaryButtonProps) {
  return (
    <button
      type="button"
      className="h-11 rounded-[10px] bg-surface px-4 font-sans text-[14.5px] font-semibold text-ink inset-ring inset-ring-line-strong"
      onClick={onClick}
    >
      {children}
    </button>
  )
}
