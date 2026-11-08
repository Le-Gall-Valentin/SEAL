import { LoadErrorState } from '@/shared/ui'

interface ErrorPageProps {
  readonly onRetry: () => void
}

/** Page de repli des deux frontières d'erreur de app (A10) : l'état « Erreur de chargement », centré. */
export function ErrorPage({ onRetry }: ErrorPageProps) {
  return (
    <main className="grid min-h-screen place-items-center p-4">
      <LoadErrorState heading="h1" onRetry={onRetry} />
    </main>
  )
}
