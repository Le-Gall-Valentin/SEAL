import type { ReactNode } from 'react'
import { useMessages } from '@/shared/i18n'
import { loadErrorStateMessages } from './loadErrorStateMessages'
import { StateCard } from './StateCard'

interface LoadErrorStateProps {
  /** Niveau du titre, selon l'endroit où l'état s'affiche : h1 quand il remplace la page. */
  readonly heading: 'h1' | 'h2' | 'h3'
  readonly onRetry: () => void
  /** Action de plus, après « Réessayer » : « Se déconnecter » quand l'échec peut persister (ADR 0029, point 10). */
  readonly extra?: ReactNode
}

/**
 * État « Erreur de chargement » de la maquette (écran Etats), sans la phrase sur le réseau, fausse pour une erreur de
 * rendu (ADR 0012). Annoncé dès son affichage.
 */
export function LoadErrorState({ heading, onRetry, extra }: LoadErrorStateProps) {
  const t = useMessages(loadErrorStateMessages)
  return (
    <StateCard
      heading={heading}
      title={t('title')}
      actions={
        <div className="flex flex-wrap items-start gap-2">
          <button
            type="button"
            className="inline-flex h-11 items-center gap-2 rounded-[10px] bg-surface pr-4 pl-3 font-sans text-[14.5px] font-semibold text-ink inset-ring inset-ring-line-strong"
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
          {extra}
        </div>
      }
    />
  )
}
