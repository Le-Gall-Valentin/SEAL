import { useId } from 'react'
import { t } from '@/shared/i18n'
import styles from './LoadErrorState.module.css'

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
  return (
    <section role="alert" aria-labelledby={titleId} className={styles['card']}>
      <span aria-hidden="true" className={styles['icon']}>
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
      <Heading id={titleId} className={styles['title']}>
        {t('loadError.title')}
      </Heading>
      <button type="button" className={styles['retry']} onClick={onRetry}>
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
        {t('loadError.retry')}
      </button>
    </section>
  )
}
