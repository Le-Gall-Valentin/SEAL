import { type ReactNode, Suspense, use } from 'react'
import { KeyStoreProvider } from '@/entities/authority'
import { UnsupportedBrowserPage } from '@/pages/unsupported-browser'
import type { KeyStore } from '@/shared/crypto'

interface CryptoGateProps {
  /**
   * Trousseau de l'appareil, ou null si le navigateur n'a pas Ed25519 et X25519 ; préparé une seule fois par la racine
   * de composition. Un refus d'IndexedDB rejette la promesse : la frontière d'erreur l'affiche.
   */
  readonly ready: Promise<KeyStore | null>
  readonly children: ReactNode
}

/**
 * KEY-01, KEY-40 : rien ne s'affiche avant le contrôle des algorithmes et l'ouverture du trousseau. Un navigateur sans
 * les algorithmes ne voit que « Navigateur non pris en charge » ; un trousseau impossible à ouvrir mène à l'état
 * « Erreur de chargement », jamais à des clés en mémoire. Ce n'est pas une requête : aucune fin de session ne le relance.
 */
export function CryptoGate({ ready, children }: CryptoGateProps) {
  return (
    <Suspense fallback={null}>
      <Ready ready={ready}>{children}</Ready>
    </Suspense>
  )
}

function Ready({ ready, children }: CryptoGateProps) {
  const store = use(ready)
  return store === null ? (
    <UnsupportedBrowserPage />
  ) : (
    <KeyStoreProvider api={store}>{children}</KeyStoreProvider>
  )
}
