import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { renderHook } from 'vitest-browser-react'
import {
  type IKeysApi,
  InvalidVaultError,
  KEYS_LOCK,
  KeysApiProvider,
  KeyStoreProvider,
  restoreKeys,
  WrongRecoveryKeyError,
} from '@/entities/keyring'
import { type KeyStore, parseRecoveryKey } from '@/shared/crypto'
import { NetworkError, withLock } from '@/shared/lib'
import { useKeyRestore } from './useKeyRestore'

vi.mock('@/entities/keyring', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/entities/keyring')>()),
  restoreKeys: vi.fn(),
}))

const OWNER = { userId: '0192f7a1-ffff-7000-8000-00000000a11c', username: 'valentin.dupont' }
const KEY = '8WQK3MZDT7PAR2HX5NCFY9GBK4VEJ6TS'
const api = {} as IKeysApi
const store = {} as KeyStore

async function renderRestore(onRestored: () => void = () => undefined, client = new QueryClient()) {
  const wrapper = ({ children }: { readonly children: ReactNode }) => (
    <QueryClientProvider client={client}>
      <KeysApiProvider api={api}>
        <KeyStoreProvider api={store}>{children}</KeyStoreProvider>
      </KeysApiProvider>
    </QueryClientProvider>
  )
  return renderHook(() => useKeyRestore(OWNER, onRestored), { wrapper })
}

afterEach(() => {
  vi.mocked(restoreKeys).mockReset()
})

describe('KEY-33 : Restauration-Appareil, modèle', () => {
  it('KEY-33 : le bouton n’est actif qu’avec 32 caractères valides, et rien ne part avant l’envoi', async () => {
    const { result } = await renderRestore()

    expect(result.current.canSubmit).toBe(false)
    result.current.enter(0, KEY.slice(0, 28))
    await expect.poll(() => result.current.groups[6]).toBe('K4VE')
    expect(result.current.canSubmit).toBe(false)
    result.current.enter(7, 'J6TS')
    await expect.poll(() => result.current.canSubmit).toBe(true)
    expect(restoreKeys).not.toHaveBeenCalled()
  })

  it('KEY-31, KEY-33 : la clé part vers restoreKeys sous le verrou des clés, jamais en texte ; succès → la garde reprend', async () => {
    let resolveRestore: () => void = () => undefined
    vi.mocked(restoreKeys).mockImplementationOnce(
      () =>
        new Promise<void>((resolve) => {
          resolveRestore = resolve
        }),
    )
    const onRestored = vi.fn()
    const { result } = await renderRestore(onRestored)
    result.current.enter(0, KEY)
    await expect.poll(() => result.current.canSubmit).toBe(true)

    result.current.submit()
    await expect.poll(() => vi.mocked(restoreKeys).mock.calls.length).toBe(1)
    let waited = false
    const waiting = withLock(KEYS_LOCK, () => {
      waited = true
      return Promise.resolve()
    })
    await new Promise((resolve) => setTimeout(resolve, 20))
    expect(waited).toBe(false)
    expect(result.current.pending).toBe(true)
    resolveRestore()
    await waiting

    const [context, key] = vi.mocked(restoreKeys).mock.calls[0] ?? []
    expect(context?.owner).toEqual(OWNER)
    expect(key?.groups()).toEqual(parseRecoveryKey(KEY).groups())
    expect(JSON.stringify(context?.owner)).not.toContain('8WQK')
    await expect.poll(() => onRestored.mock.calls.length).toBe(1)
  })

  it('KEY-33, ADR 0033 (point 6) : un double clic sur « Restaurer » ne lance qu’une restauration', async () => {
    vi.mocked(restoreKeys).mockResolvedValue(undefined)
    const onRestored = vi.fn()
    const { result } = await renderRestore(onRestored)
    result.current.enter(0, KEY)
    await expect.poll(() => result.current.canSubmit).toBe(true)

    // Deux envois sans rendu entre eux : `pending` n'est relu qu'au rendu suivant. Sans verrou d'envoi, le verrou des clés
    // rangerait le second derrière le premier, et la restauration serait rejouée une fois finie.
    result.current.submit()
    result.current.submit()

    await expect.poll(() => onRestored.mock.calls.length).toBe(1)
    await new Promise((resolve) => setTimeout(resolve, 50))
    expect(restoreKeys).toHaveBeenCalledTimes(1)
    expect(onRestored).toHaveBeenCalledTimes(1)
  })

  // Le verrou rendu après un échec est vérifié par le test suivant : chaque refus est suivi d'un nouvel envoi.
  it('ADR 0029, point 5 : clé fausse, coffre invalide et réseau donnent chacun leur refus ; une saisie l’efface', async () => {
    const { result } = await renderRestore()
    result.current.enter(0, KEY)
    await expect.poll(() => result.current.canSubmit).toBe(true)

    for (const [error, kind] of [
      [new WrongRecoveryKeyError(), 'wrong-key'],
      [new InvalidVaultError(), 'invalid'],
      [new NetworkError(), 'network'],
      [new Error('inattendue'), 'unexpected'],
    ] as const) {
      vi.mocked(restoreKeys).mockRejectedValueOnce(error)
      result.current.submit()
      await expect.poll(() => result.current.failure?.kind).toBe(kind)
    }
    result.current.enter(3, 'R2HX')
    await expect.poll(() => result.current.failure).toBeNull()
  })

  it('KEY-31, KEY-40 : la clé de récupération ne reste pas dans le cache des mutations, ni pendant ni après', async () => {
    let resolveRestore: () => void = () => undefined
    vi.mocked(restoreKeys).mockImplementationOnce(
      () =>
        new Promise<void>((resolve) => {
          resolveRestore = resolve
        }),
    )
    const client = new QueryClient()
    const { result, unmount } = await renderRestore(() => undefined, client)
    result.current.enter(0, KEY)
    await expect.poll(() => result.current.canSubmit).toBe(true)
    const variables = () =>
      client
        .getMutationCache()
        .getAll()
        .map((mutation) => mutation.state.variables)

    result.current.submit()
    await expect.poll(() => vi.mocked(restoreKeys).mock.calls.length).toBe(1)
    expect(variables()).toEqual([undefined])
    resolveRestore()
    await expect.poll(() => result.current.pending).toBe(false)
    expect(variables()).toEqual([undefined])

    await unmount()
    await expect.poll(() => client.getMutationCache().getAll()).toHaveLength(0)
  })
})
