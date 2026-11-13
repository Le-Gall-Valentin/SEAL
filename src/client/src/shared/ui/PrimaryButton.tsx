import type { ReactNode } from 'react'

interface PrimaryButtonProps {
  readonly disabled?: boolean
  readonly children: ReactNode
}

/** Bouton d'envoi d'un formulaire (« Se connecter »…), estompé quand il est désactivé. */
export function PrimaryButton({ disabled = false, children }: PrimaryButtonProps) {
  return (
    <button
      type="submit"
      disabled={disabled}
      className="h-11 rounded-[10px] bg-accent font-sans text-[14.5px] font-semibold text-on-accent disabled:opacity-55"
    >
      {children}
    </button>
  )
}
