import { type ReactNode, useId } from 'react'

interface StateCardProps {
  /** Niveau du titre, selon l'endroit où l'état s'affiche : h1 quand il remplace la page. */
  readonly heading: 'h1' | 'h2' | 'h3'
  readonly title: string
  readonly text?: string
  readonly actions?: ReactNode
}

/**
 * Carte de l'état « Erreur de chargement » de la maquette (écran Etats) : icône, titre, texte et actions posées en bas.
 * Elle garde 236 px de haut et grandit pour un texte plus long, sans rien couper (ADR 0027, point 13). Son titre et son
 * texte sont annoncés dès l'affichage ; ses actions restent hors de l'alerte, pour qu'une alerte qu'elles affichent ne
 * s'y imbrique pas.
 */
export function StateCard({ heading: Heading, title, text, actions }: StateCardProps) {
  const titleId = useId()
  return (
    <section
      aria-labelledby={titleId}
      className="flex min-h-59 w-full max-w-95 flex-col items-start rounded-[18px] bg-surface p-5.5 inset-ring inset-ring-line"
    >
      <div role="alert" className="flex flex-col items-start">
        <span
          aria-hidden="true"
          className="grid size-11 flex-none place-items-center rounded-xl bg-danger-soft text-danger inset-ring inset-ring-danger-line"
        >
          <svg
            width="22"
            height="22"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="1.8"
            strokeLinecap="round"
          >
            <circle cx="12" cy="12" r="8.5" />
            <path d="M12 7.5v5.2M12 16.2h.01" />
          </svg>
        </span>
        <Heading id={titleId} className="mt-3.5 text-[18px] font-bold tracking-[-0.01em]">
          {title}
        </Heading>
        {text === undefined ? null : (
          <p className="mt-1.5 text-[14px] leading-[1.45] text-ink-2">{text}</p>
        )}
      </div>
      {actions === undefined ? null : <div className="mt-auto flex pt-4">{actions}</div>}
    </section>
  )
}
