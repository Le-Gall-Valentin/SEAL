import { LogoutButton } from '@/features/logout'
import { Wordmark } from '@/shared/ui'

/**
 * Accueil provisoire du lot 1 (ADR 0024, point 2) : le logo et le seul bouton « Se déconnecter », jusqu'aux
 * conversations (lot 3) et à la restauration des clés (noyau cryptographique). Écart temporaire à la maquette.
 */
export function HomePage() {
  return (
    <main className="flex min-h-screen flex-col items-center justify-center gap-6 bg-bg px-5 font-sans text-ink">
      <h1 className="m-0">
        <Wordmark variant="panel" />
      </h1>
      <div className="w-full max-w-60">
        <LogoutButton />
      </div>
    </main>
  )
}
