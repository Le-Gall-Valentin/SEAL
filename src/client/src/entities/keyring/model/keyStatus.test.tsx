import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { describe, expect, it } from 'vitest'
import { renderHook } from 'vitest-browser-react'
import type { KeyStore } from '@/shared/crypto'
import { ClockProvider, withLock } from '@/shared/lib'
import { KeysApiProvider } from './keysApiPort'
import { KEYS_LOCK, useKeyStatus } from './keyStatus'
import { KeyStoreProvider } from './keyStorePort'
import { fakeKeysServer, type FakeKeysServer } from './testing/fakeKeysServer'
import {
  pinnedStore,
  VECTOR_CERTIFICATE,
  VECTOR_OWNER,
  VECTOR_SUBKEY_CREATED_AT,
  VECTOR_VAULT,
  vectorKeyring,
} from './testing/keyVectors'

const NOW = VECTOR_SUBKEY_CREATED_AT + 60_000

/** Un onglet : son propre cache de requêtes, le serveur et l'appareil partagés. */
async function renderStatus(server: FakeKeysServer, store: KeyStore) {
  const client = new QueryClient()
  const wrapper = ({ children }: { readonly children: ReactNode }) => (
    <QueryClientProvider client={client}>
      <ClockProvider clock={{ now: () => NOW }}>
        <KeysApiProvider api={server}>
          <KeyStoreProvider api={store}>{children}</KeyStoreProvider>
        </KeysApiProvider>
      </ClockProvider>
    </QueryClientProvider>
  )
  return renderHook(() => useKeyStatus(VECTOR_OWNER), { wrapper })
}

/** Trousseau des vecteurs sur l'appareil, rien de publié : le statut reprend la publication. */
async function interrupted(): Promise<{ server: FakeKeysServer; store: KeyStore }> {
  const server = fakeKeysServer(VECTOR_CERTIFICATE, () => NOW)
  server.vault = VECTOR_VAULT
  const store = await pinnedStore()
  await store.saveKeyring(await vectorKeyring())
  return { server, store }
}

describe('KEY-02, KEY-33 : statut des clés dans la zone du compte (ADR 0029, point 3)', () => {
  it('ADR 0029, point 3 : deux onglets qui lisent le statut ensemble, sous le verrou, ne publient qu’une fois', async () => {
    const { server, store } = await interrupted()

    // Montés l'un après l'autre (act() ne se chevauche pas) : la lecture du premier tient encore le verrou quand le
    // second demande le sien.
    const first = await renderStatus(server, store)
    const second = await renderStatus(server, store)

    await expect.poll(() => first.result.current.status).toBe('ready')
    await expect.poll(() => second.result.current.status).toBe('ready')
    expect(server.calls.filter((call) => call === 'publishIdentity')).toHaveLength(1)
    expect(server.subkeys).toHaveLength(1)
    await first.unmount()
    await second.unmount()
  })

  it('A10 : une première lecture qui échoue n’est pas relancée d’elle-même, et « failed » le dit', async () => {
    const { server, store } = await interrupted()
    server.fail('readOwnKeys', 'before')

    const { result } = await renderStatus(server, store)

    await expect.poll(() => result.current.failed).toBe(true)
    expect(result.current.status).toBeUndefined()
    expect(server.calls).toEqual(['readOwnKeys'])
  })

  it('ADR 0029, point 13 : un verrou tenu par un autre onglet fait attendre, et « waiting » le dit jusqu’au statut', async () => {
    const { server, store } = await interrupted()
    let release: () => void = () => undefined
    const holder = withLock(
      KEYS_LOCK,
      () =>
        new Promise<void>((resolve) => {
          release = resolve
        }),
    )

    const { result } = await renderStatus(server, store)

    await expect.poll(() => result.current.waiting).toBe(true)
    expect(result.current.status).toBeUndefined()
    release()
    await holder
    await expect.poll(() => result.current.status).toBe('ready')
    expect(result.current.waiting).toBe(false)
  })

  it('ADR 0029, point 10 : une relecture demandée repart de zéro, sans garder l’ancien statut', async () => {
    const server = fakeKeysServer(VECTOR_CERTIFICATE, () => NOW)
    const store = await pinnedStore()
    const { result } = await renderStatus(server, store)
    await expect.poll(() => result.current.status).toBe('setup')

    server.fail('readVault', 'before')
    result.current.reread()

    await expect.poll(() => result.current.failed).toBe(true)
    expect(result.current.status).toBeUndefined()
  })
})
