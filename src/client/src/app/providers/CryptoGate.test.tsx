import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, expect, it, vi } from 'vitest'
import { render } from 'vitest-browser-react'
import { AuthorityApiProvider, useAuthorityCheck } from '@/entities/authority'
import type { KeyStore } from '@/shared/crypto'
import { memoryKeyStore, servingAuthority } from '../testing/authority'
import { CryptoGate } from './CryptoGate'
import { ErrorBoundary } from './ErrorBoundary'

const NEVER = new Promise<KeyStore | null>(() => undefined)

/** Lit le contrôle de l'Autorité : il n'aboutit que si le trousseau est fourni. */
function AuthorityProbe() {
  const check = useAuthorityCheck()
  return <p>{check.data ?? '…'}</p>
}

function renderGate(ready: Promise<KeyStore | null>) {
  return render(
    <ErrorBoundary>
      <CryptoGate ready={ready}>
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
    const screen = await renderGate(Promise.resolve(null))

    await expect
      .element(screen.getByRole('heading', { level: 1, name: 'Navigateur non pris en charge' }))
      .toBeVisible()
    expect(screen.getByText('Application').elements()).toHaveLength(0)
  })

  it('KEY-40 : un navigateur qui les propose voit l’application, avec le trousseau de l’appareil', async () => {
    const screen = await renderGate(Promise.resolve(memoryKeyStore()))

    await expect.element(screen.getByText('Application')).toBeVisible()
    await expect.element(screen.getByText('pinned')).toBeVisible()
  })

  it('KEY-01 : tant que le contrôle n’a pas répondu, rien ne s’affiche', async () => {
    const screen = await renderGate(NEVER)

    await new Promise((resolve) => setTimeout(resolve, 50))
    expect(screen.container.textContent).toBe('')
  })

  it('KEY-40 : un trousseau impossible à ouvrir mène à « Impossible de charger », jamais à des clés en mémoire', async () => {
    vi.spyOn(console, 'error').mockImplementation(() => undefined)
    const screen = await renderGate(Promise.reject(new DOMException('refus', 'InvalidStateError')))

    await expect
      .element(screen.getByRole('heading', { name: 'Impossible de charger' }))
      .toBeVisible()
    expect(screen.getByText('Application').elements()).toHaveLength(0)
  })
})
