import type { ReactNode } from 'react'

interface StatePageProps {
  readonly children: ReactNode
}

/** Page pleine d'un état (écran Etats) : la carte centrée, marges de 16 px (ADR 0012, ADR 0027). */
export function StatePage({ children }: StatePageProps) {
  return <main className="grid min-h-screen place-items-center p-4">{children}</main>
}
