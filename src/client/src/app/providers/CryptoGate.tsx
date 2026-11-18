import { type ReactNode, Suspense, use } from 'react'
import { UnsupportedBrowserPage } from '@/pages/unsupported-browser'

interface CryptoGateProps {
  /** Résultat du contrôle de KEY-01, lancé une seule fois par la racine de composition. */
  readonly support: Promise<boolean>
  readonly children: ReactNode
}

/**
 * KEY-01 : rien ne s'affiche avant le contrôle d'Ed25519 et de X25519, et un navigateur qui ne les propose pas ne voit
 * que « Navigateur non pris en charge ». Le contrôle n'est pas une requête : aucune fin de session ne le relance.
 */
export function CryptoGate({ support, children }: CryptoGateProps) {
  return (
    <Suspense fallback={null}>
      <Supported support={support}>{children}</Supported>
    </Suspense>
  )
}

function Supported({ support, children }: CryptoGateProps) {
  return use(support) ? children : <UnsupportedBrowserPage />
}
