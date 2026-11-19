import { focusManager, QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { renderHook } from 'vitest-browser-react'
import {
  ForeignIdentityError,
  type IKeysApi,
  KEYS_LOCK,
  KeysApiProvider,
  KeyStoreProvider,
  setupKeys,
  StaleVaultError,
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

/** Les deux groupes tirés, ressaisis justes : « Terminer » devient possible. */
async function confirm(result: { readonly current: ReturnType<typeof useKeySetup> }) {
  await expect.poll(() => result.current.ready).toBe(true)
  const [first, second] = result.current.picked
  result.current.type(0, result.current.groups[first] ?? '')
  result.current.type(1, result.current.groups[second] ?? '')
  await expect.poll(() => result.current.confirmed).toBe(true)
}

/** Vrai si un autre onglet obtiendrait le verrou des clés tout de suite. */
async function lockIsFree(): Promise<boolean> {
  return navigator.locks.request(KEYS_LOCK, { ifAvailable: true }, (lock) => lock !== null)
}

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
    await expect.poll(() => result.current.failure).toBe('network')
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

  it('ADR 0029, point 3 : un retour du focus pendant « Terminer » ne rend ni le verrou ni la main avant la fin', async () => {
    const onDone = vi.fn()
    let finishSetup: () => void = () => undefined
    vi.mocked(setupKeys).mockImplementationOnce(async ({ store }, pending) => {
      await store.saveKeyring(pending.keyring)
      await new Promise<void>((resolve) => {
        finishSetup = resolve
      })
      return 'done'
    })
    const { result } = await renderSetup(await freshStore(), onDone)
    await confirm(result)

    result.current.finish()
    await expect.poll(() => result.current.saving).toBe(true)
    await new Promise((resolve) => setTimeout(resolve, 20))
    focusManager.setFocused(false)
    focusManager.setFocused(true)
    await new Promise((resolve) => setTimeout(resolve, 50))

    expect(onDone).not.toHaveBeenCalled()
    expect(await lockIsFree()).toBe(false)
    finishSetup()
    await expect.poll(() => onDone.mock.calls.length).toBe(1)
    focusManager.setFocused(undefined)
  })

  it('ADR 0029, point 2 : deux « Terminer » avant le rendu suivant ne lancent qu’une sauvegarde', async () => {
    vi.mocked(setupKeys).mockResolvedValue('done')
    const { result } = await renderSetup(await freshStore(), () => undefined)
    await confirm(result)

    result.current.finish()
    result.current.finish()

    await expect.poll(() => vi.mocked(setupKeys).mock.calls.length).toBe(1)
    await new Promise((resolve) => setTimeout(resolve, 50))
    expect(setupKeys).toHaveBeenCalledOnce()
  })

  it('ADR 0029, point 5 : un certificat d’une autre identité ou un coffre rejoué rendent la main à la zone du compte', async () => {
    for (const error of [new ForeignIdentityError(), new StaleVaultError()]) {
      const onDone = vi.fn()
      vi.mocked(setupKeys).mockRejectedValueOnce(error)
      const { result, unmount } = await renderSetup(await freshStore(), onDone)
      await confirm(result)

      result.current.finish()

      await expect.poll(() => onDone.mock.calls.length).toBe(1)
      expect(await lockIsFree()).toBe(true)
      await unmount()
    }
  })

  it('ADR 0029, point 12 : une erreur réseau garde son texte, toute autre erreur est inattendue', async () => {
    vi.mocked(setupKeys).mockRejectedValueOnce(new Error('IndexedDB indisponible'))
    const { result } = await renderSetup(await freshStore(), () => undefined)
    await confirm(result)

    result.current.finish()

    await expect.poll(() => result.current.failure).toBe('unexpected')
  })

  it('ADR 0029, point 3 : un trousseau illisible sur l’appareil rend le verrou et la main, sans page blanche', async () => {
    const store = await freshStore()
    const onDone = vi.fn()

    await renderSetup(
      { ...store, loadKeyring: () => Promise.reject(new Error('IndexedDB indisponible')) },
      onDone,
    )

    await expect.poll(() => onDone.mock.calls.length).toBe(1)
    expect(await lockIsFree()).toBe(true)
  })
})
