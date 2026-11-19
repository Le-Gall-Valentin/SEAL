import { LoadErrorState, StatePage } from '@/shared/ui'

interface ErrorPageProps {
  readonly onRetry: () => void
}

/** Page de repli des deux frontières d'erreur de app (A10) : l'état « Erreur de chargement », centré. */
export function ErrorPage({ onRetry }: ErrorPageProps) {
  return (
    <StatePage>
      <LoadErrorState heading="h1" onRetry={onRetry} />
    </StatePage>
  )
}
