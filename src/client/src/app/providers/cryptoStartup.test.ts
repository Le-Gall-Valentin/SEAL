import { describe, expect, it, vi } from 'vitest'
import type { KeyStore } from '@/shared/crypto'
import { createCryptoStartup } from './cryptoStartup'

/** Seule son identité compte ici : le démarrage le rend tel quel. */
const STORE: KeyStore = {
  loadKeyring: () => Promise.resolve(undefined),
  saveKeyring: () => Promise.resolve(),
  loadCertificate: () => Promise.resolve(undefined),
  loadAuthority: () => Promise.resolve(undefined),
  close: () => undefined,
}

describe('KEY-01, KEY-40 : démarrage cryptographique du client (ADR 0027, point 8)', () => {
  it('KEY-01 : un navigateur sans les algorithmes n’ouvre rien et n’envoie aucune requête', async () => {
    const openStore = vi.fn(() => Promise.resolve(STORE))
    const onSupported = vi.fn()
    const start = createCryptoStartup({
      checkSupport: () => Promise.resolve(false),
      openStore,
      onSupported,
    })

    await expect(start()).resolves.toBeNull()
    expect(openStore).not.toHaveBeenCalled()
    expect(onSupported).not.toHaveBeenCalled()
  })

  it('KEY-40 : un navigateur accepté recharge le jeton CSRF, puis ouvre le trousseau', async () => {
    const onSupported = vi.fn()
    const start = createCryptoStartup({
      checkSupport: () => Promise.resolve(true),
      openStore: () => Promise.resolve(STORE),
      onSupported,
    })

    await expect(start()).resolves.toBe(STORE)
    expect(onSupported).toHaveBeenCalledOnce()
  })

  it('KEY-40 : un refus d’IndexedDB rejette, et chaque appel retente l’ouverture', async () => {
    const openStore = vi
      .fn<() => Promise<KeyStore>>()
      .mockRejectedValueOnce(new DOMException('refus', 'InvalidStateError'))
      .mockResolvedValueOnce(STORE)
    const start = createCryptoStartup({
      checkSupport: () => Promise.resolve(true),
      openStore,
      onSupported: () => undefined,
    })

    await expect(start()).rejects.toThrow('refus')
    await expect(start()).resolves.toBe(STORE)
  })
})
