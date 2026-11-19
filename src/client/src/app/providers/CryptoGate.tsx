import { type ReactNode, Suspense, use, useState } from 'react'
import { KeyStoreProvider } from '@/entities/authority'
import { UnsupportedBrowserPage } from '@/pages/unsupported-browser'
import type { KeyStore } from '@/shared/crypto'
import { ErrorPage } from '../layouts'

interface CryptoGateProps {
  /** Démarrage cryptographique (createCryptoStartup) : le trousseau, ou null si le navigateur est refusé. */
  readonly start: () => Promise<KeyStore | null>
  readonly children: ReactNode
}

type Startup =
  | { readonly status: 'ready'; readonly store: KeyStore }
  | { readonly status: 'unsupported' }
  | { readonly status: 'failed' }

/** Le résultat du démarrage, échec compris : une erreur de rendu de l'application ne relance pas le démarrage. */
function settle(start: () => Promise<KeyStore | null>): Promise<Startup> {
  return start().then(
    (store): Startup => (store === null ? { status: 'unsupported' } : { status: 'ready', store }),
    (): Startup => ({ status: 'failed' }),
  )
}

/**
 * KEY-01, KEY-40 : rien ne s'affiche avant le contrôle des algorithmes et l'ouverture du trousseau. Un navigateur sans
 * les algorithmes ne voit que « Navigateur non pris en charge » ; un trousseau impossible à ouvrir mène à « Impossible
 * de charger », dont « Réessayer » relance le démarrage (ADR 0027, point 8), jamais à des clés en mémoire. Ce n'est pas
 * une requête : aucune fin de session ne le relance.
 */
export function CryptoGate({ start, children }: CryptoGateProps) {
  const [startup, setStartup] = useState(() => settle(start))
  return (
    <Suspense fallback={null}>
      <Ready
        startup={startup}
        onRetry={() => {
          setStartup(settle(start))
        }}
      >
        {children}
      </Ready>
    </Suspense>
  )
}

interface ReadyProps {
  readonly startup: Promise<Startup>
  readonly onRetry: () => void
  readonly children: ReactNode
}

function Ready({ startup, onRetry, children }: ReadyProps) {
  const result = use(startup)
  switch (result.status) {
    case 'unsupported':
      return <UnsupportedBrowserPage />
    case 'failed':
      return <ErrorPage onRetry={onRetry} />
    case 'ready':
      return <KeyStoreProvider api={result.store}>{children}</KeyStoreProvider>
  }
}
