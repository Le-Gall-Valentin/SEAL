import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { Component, type ReactNode } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { render } from 'vitest-browser-react'
import { AuthorityApiProvider, useAuthorityCheck } from '@/entities/authority'
import type { KeyStore } from '@/shared/crypto'
import { freshKeyStore, servingAuthority } from '../testing/authority'
import { CryptoGate } from './CryptoGate'
import { ErrorBoundary } from './ErrorBoundary'

const never = () => new Promise<KeyStore | null>(() => undefined)

/** Frontière reconnaissable : seule une erreur qui sort de CryptoGate l'atteint. */
class OuterBoundary extends Component<
  { readonly children: ReactNode },
  { readonly failed: boolean }
> {
  override state = { failed: false }

  static getDerivedStateFromError() {
    return { failed: true }
  }

  override render() {
    return this.state.failed ? <p>Frontière extérieure</p> : this.props.children
  }
}

/** Lit le contrôle de l'Autorité : il n'aboutit que si le trousseau est fourni. */
function AuthorityProbe() {
  const check = useAuthorityCheck()
  return <p>{check.data ?? '…'}</p>
}

function renderGate(start: () => Promise<KeyStore | null>) {
  return render(
    <ErrorBoundary>
      <CryptoGate start={start}>
        <QueryClientProvider client={new QueryClient()}>
          <AuthorityApiProvider api={servingAuthority()}>
            <p>Application</p>
            <AuthorityProbe />
          </AuthorityApiProvider>
        </QueryClientProvider>
      </CryptoGate>
    </ErrorBoundary>,
  )
}

describe('KEY-01, KEY-40 : rien ne fonctionne sans les algorithmes ni le trousseau', () => {
  it('KEY-01 : un navigateur sans Ed25519 ni X25519 ne voit que « Navigateur non pris en charge »', async () => {
    const screen = await renderGate(() => Promise.resolve(null))

    await expect
      .element(screen.getByRole('heading', { level: 1, name: 'Navigateur non pris en charge' }))
      .toBeVisible()
    expect(screen.getByText('Application').elements()).toHaveLength(0)
  })

  it('KEY-40 : un navigateur qui les propose voit l’application, avec le trousseau de l’appareil', async () => {
    const screen = await renderGate(() => freshKeyStore())

    await expect.element(screen.getByText('Application')).toBeVisible()
    await expect.element(screen.getByText('pinned')).toBeVisible()
  })

  it('KEY-01 : tant que le contrôle n’a pas répondu, rien ne s’affiche', async () => {
    const screen = await renderGate(never)

    await new Promise((resolve) => setTimeout(resolve, 50))
    expect(screen.container.textContent).toBe('')
  })

  it('KEY-40 : un trousseau impossible à ouvrir mène à « Impossible de charger », jamais à des clés en mémoire', async () => {
    const screen = await renderGate(() =>
      Promise.reject(new DOMException('refus', 'InvalidStateError')),
    )

    await expect
      .element(screen.getByRole('heading', { name: 'Impossible de charger' }))
      .toBeVisible()
    expect(screen.getByText('Application').elements()).toHaveLength(0)
  })

  it('ADR 0027, point 8 : « Réessayer » relance l’ouverture du trousseau', async () => {
    let attempts = 0
    const screen = await renderGate(() => {
      attempts += 1
      return attempts === 1
        ? Promise.reject(new DOMException('refus', 'InvalidStateError'))
        : freshKeyStore()
    })

    await screen.getByRole('button', { name: 'Réessayer' }).click()

    await expect.element(screen.getByText('Application')).toBeVisible()
    expect(attempts).toBe(2)
  })

  it('A10 : une erreur de rendu de l’application va à la frontière extérieure, pas à l’échec du démarrage', async () => {
    vi.spyOn(console, 'error').mockImplementation(() => undefined)
    function Broken(): never {
      throw new Error('rendu')
    }
    const screen = await render(
      <OuterBoundary>
        <CryptoGate start={() => freshKeyStore()}>
          <Broken />
        </CryptoGate>
      </OuterBoundary>,
    )

    await expect.element(screen.getByText('Frontière extérieure')).toBeVisible()
    expect(screen.getByRole('heading', { name: 'Impossible de charger' }).elements()).toHaveLength(
      0,
    )
  })
})
