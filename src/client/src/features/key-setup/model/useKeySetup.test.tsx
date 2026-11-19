import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { renderHook } from 'vitest-browser-react'
import {
  type IKeysApi,
  KEYS_LOCK,
  KeysApiProvider,
  KeyStoreProvider,
  setupKeys,
} from '@/entities/keyring'
import { generateKeyring, generateRecoveryKey, type KeyStore, openKeyStore } from '@/shared/crypto'
import { NetworkError, withLock } from '@/shared/lib'
import { useKeySetup } from './useKeySetup'

vi.mock('@/entities/keyring', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/entities/keyring')>()),
  setupKeys: vi.fn(),
}))

const OWNER = { userId: '0192f7a1-ffff-7000-8000-00000000a11c', username: 'valentin.dupont' }
const calls: string[] = []

/** Toute requête est notée : aucune ne doit partir avant « Terminer ». */
const api = new Proxy({} as IKeysApi, {
  get: (_target, method: string) => () => {
    calls.push(method)
    return Promise.reject(new Error('non attendu'))
  },
})

let opened = 0

async function freshStore(): Promise<KeyStore> {
  opened += 1
  return openKeyStore(`seal-key-setup-test-${String(Date.now())}-${String(opened)}`)
}

async function renderSetup(store: KeyStore, onDone: () => void) {
  const wrapper = ({ children }: { readonly children: ReactNode }) => (
    <QueryClientProvider client={new QueryClient()}>
      <KeysApiProvider api={api}>
        <KeyStoreProvider api={store}>{children}</KeyStoreProvider>
      </KeysApiProvider>
    </QueryClientProvider>
  )
  return renderHook(() => useKeySetup(OWNER, onDone), { wrapper })
}

afterEach(() => {
  calls.length = 0
  vi.mocked(setupKeys).mockReset()
})

describe('KEY-02, KEY-30 : Dialogue-Cle-Recuperation, modèle', () => {
  it('KEY-02, KEY-30 : rien n’est envoyé avant « Terminer », et seuls les deux groupes tirés confirment la clé', async () => {
    const { result } = await renderSetup(await freshStore(), () => undefined)
    await expect.poll(() => result.current.ready).toBe(true)
    const [first, second] = result.current.picked

    expect(result.current.groups).toHaveLength(8)
    expect(first).not.toBe(second)
    result.current.type(0, 'ZZZZ')
    await expect.poll(() => result.current.mismatch(0)).toBe(true)
    result.current.type(0, (result.current.groups[first] ?? '').toLowerCase())
    result.current.type(1, result.current.groups[second] ?? '')
    await expect.poll(() => result.current.confirmed).toBe(true)
    expect(result.current.mismatch(0)).toBe(false)
    expect(calls).toEqual([])
    expect(setupKeys).not.toHaveBeenCalled()
  })

  it('ADR 0029 : « Terminer » génère le trousseau une fois, le garde après un échec, puis rend la main', async () => {
    const onDone = vi.fn()
    vi.mocked(setupKeys).mockRejectedValueOnce(new NetworkError()).mockResolvedValueOnce('done')
    const { result } = await renderSetup(await freshStore(), onDone)
    await expect.poll(() => result.current.ready).toBe(true)
    const [first, second] = result.current.picked
    result.current.type(0, result.current.groups[first] ?? '')
    result.current.type(1, result.current.groups[second] ?? '')
    await expect.poll(() => result.current.confirmed).toBe(true)

    result.current.finish()
    await expect.poll(() => result.current.failed).toBe(true)
    result.current.finish()
    await expect.poll(() => onDone.mock.calls.length).toBe(1)

    const [firstPending, secondPending] = vi
      .mocked(setupKeys)
      .mock.calls.map(([, pending]) => pending)
    expect(secondPending).toBe(firstPending)
    expect(firstPending?.keyring.userId).toBe(OWNER.userId)
  })

  it('ADR 0029 : le verrou des clés est tenu pendant le dialogue et rendu à la fin', async () => {
    vi.mocked(setupKeys).mockResolvedValueOnce('done')
    const { result } = await renderSetup(await freshStore(), () => undefined)
    await expect.poll(() => result.current.ready).toBe(true)
    let waited = false
    const waiting = withLock(KEYS_LOCK, () => {
      waited = true
      return Promise.resolve()
    })
    await new Promise((resolve) => setTimeout(resolve, 20))
    expect(waited).toBe(false)

    const [first, second] = result.current.picked
    result.current.type(0, result.current.groups[first] ?? '')
    result.current.type(1, result.current.groups[second] ?? '')
    await expect.poll(() => result.current.confirmed).toBe(true)
    result.current.finish()

    await waiting
    expect(waited).toBe(true)
  })

  it('ADR 0029 : un trousseau créé par un autre onglet pendant l’attente ferme le dialogue sans montrer de clé', async () => {
    const store = await freshStore()
    const { keyring } = await generateKeyring(OWNER.userId, generateRecoveryKey(), Date.now())
    await store.saveKeyring(keyring)
    const onDone = vi.fn()

    const { result } = await renderSetup(store, onDone)

    await expect.poll(() => onDone.mock.calls.length).toBe(1)
    expect(result.current.ready).toBe(false)
  })
})
