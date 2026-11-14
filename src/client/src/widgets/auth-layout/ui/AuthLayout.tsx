import type { ReactNode } from 'react'
import { Phoque, type PhoqueVariant, Wordmark } from '@/shared/ui'
import { BrandPanel } from './BrandPanel'

interface AuthLayoutProps {
  /** Pose de la mascotte dans l'en-tête du téléphone et de la tablette, propre à chaque écran. */
  readonly variant: PhoqueVariant
  readonly children: ReactNode
}

/**
 * ADR 0007, point 7 : mise en page commune des écrans d'authentification, d'après Connexion-Ordinateur (panneau de
 * marque et formulaire, dès 1024 px), Connexion-Tablette (logo centré et carte, dès 640 px) et Connexion-Telephone.
 * ADR 0024, point 11 : sur ordinateur, panneau et formulaire prennent chacun la moitié de la largeur.
 */
export function AuthLayout({ variant, children }: AuthLayoutProps) {
  return (
    <div className="flex min-h-screen bg-bg font-sans text-ink lg:grid lg:grid-cols-2">
      <BrandPanel />
      <main className="flex min-h-screen min-w-0 flex-1 flex-col px-5 pt-5 pb-7 sm:items-center sm:justify-center sm:gap-6 sm:px-8 sm:py-16 lg:px-6 lg:py-12">
        <header className="mb-5.5 flex items-center gap-2.5 sm:mb-0 sm:flex-col sm:gap-3 lg:hidden">
          <span className="sm:hidden">
            <Phoque variant={variant} size={64} />
          </span>
          <span className="hidden sm:block">
            <Phoque variant={variant} size={88} />
          </span>
          <Wordmark variant="header" />
        </header>
        <div className="flex w-full flex-col sm:w-120 sm:rounded-[18px] sm:bg-surface sm:px-9 sm:pt-8 sm:pb-9 sm:shadow-card lg:w-full lg:max-w-[400px] lg:rounded-none lg:bg-transparent lg:p-0 lg:shadow-none">
          {children}
        </div>
      </main>
    </div>
  )
}
