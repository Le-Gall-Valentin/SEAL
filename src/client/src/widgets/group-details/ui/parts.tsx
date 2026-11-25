import type { ReactNode } from 'react'

/** Étiquette d'un membre : « Admin » accentuée, « Pas dans vos amis » cerclée (Conv-Ordinateur, Conv-Tablette). */
export function Tag({
  accent = false,
  children,
}: {
  readonly accent?: boolean
  readonly children: ReactNode
}) {
  return (
    <span
      className={`inline-flex h-5.5 items-center rounded-full px-2 text-[12px] font-semibold ${
        accent ? 'bg-accent-soft text-accent-text' : 'text-ink-2 inset-ring inset-ring-line-strong'
      }`}
    >
      {children}
    </span>
  )
}

/** Lien d'action de la maquette (« Renommer le groupe », « Ajouter des membres »), 13 px, 36 px de haut. */
export function LinkButton({
  onClick,
  children,
}: {
  readonly onClick: () => void
  readonly children: ReactNode
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="inline-flex min-h-9 items-center gap-1.5 text-[13px] font-semibold text-accent-text"
    >
      {children}
    </button>
  )
}
