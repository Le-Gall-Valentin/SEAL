import { useId } from 'react'
import { useMessages } from '@/shared/i18n'
import { loadErrorStateMessages } from './loadErrorStateMessages'

interface LoadErrorStateProps {
  /** Niveau du titre, selon l'endroit où l'état s'affiche : h1 quand il remplace la page. */
  readonly heading: 'h1' | 'h2' | 'h3'
  readonly onRetry: () => void
}

/**
 * État « Erreur de chargement » de la maquette (écran Etats), sans la phrase sur le réseau, fausse pour une erreur de
 * rendu (ADR 0012). Annoncé dès son affichage.
 */
export function LoadErrorState({ heading: Heading, onRetry }: LoadErrorStateProps) {
  const titleId = useId()
  const t = useMessages(loadErrorStateMessages)
  return (
    <section
      role="alert"
      aria-labelledby={titleId}
      className="flex h-59 w-full max-w-95 flex-col items-start rounded-[18px] bg-surface p-5.5 inset-ring inset-ring-line"
    >
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
        {t('title')}
      </Heading>
      <button
        type="button"
        className="mt-auto inline-flex h-11 items-center gap-2 rounded-[10px] bg-surface pr-4 pl-3 font-sans text-[14.5px] font-semibold text-ink inset-ring inset-ring-line-strong"
        onClick={onRetry}
      >
        <svg
          width="18"
          height="18"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="1.8"
          strokeLinecap="round"
          strokeLinejoin="round"
          aria-hidden="true"
        >
          <path d="M19.5 12a7.5 7.5 0 1 1-2.2-5.3M19.5 4.5v4h-4" />
        </svg>
        {t('retry')}
      </button>
    </section>
  )
}
