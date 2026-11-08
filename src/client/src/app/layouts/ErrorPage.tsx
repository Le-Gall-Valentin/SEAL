import { LoadErrorState } from '@/shared/ui'
import styles from './ErrorPage.module.css'

interface ErrorPageProps {
  readonly onRetry: () => void
}

/** Page de repli des deux frontières d'erreur de app (A10) : l'état « Erreur de chargement », centré. */
export function ErrorPage({ onRetry }: ErrorPageProps) {
  return (
    <main className={styles['page']}>
      <LoadErrorState heading="h1" onRetry={onRetry} />
    </main>
  )
}
