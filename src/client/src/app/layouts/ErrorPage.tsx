import type { ReactNode } from 'react'
import { LoadErrorState, StatePage } from '@/shared/ui'

interface ErrorPageProps {
  readonly onRetry: () => void
  /** Action de plus, après « Réessayer » (ADR 0029, point 10). */
  readonly extra?: ReactNode
}

/** Page de repli des deux frontières d'erreur de app (A10) : l'état « Erreur de chargement », centré. */
export function ErrorPage({ onRetry, extra }: ErrorPageProps) {
  return (
    <StatePage>
      <LoadErrorState heading="h1" onRetry={onRetry} extra={extra} />
    </StatePage>
  )
}
