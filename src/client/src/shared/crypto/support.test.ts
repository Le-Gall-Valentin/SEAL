import { afterEach, describe, expect, it, vi } from 'vitest'
import { checkCryptoSupport } from './support'

afterEach(() => {
  vi.restoreAllMocks()
})

/** Algorithme demandé à generateKey, qu'il soit donné par son nom ou par un objet. */
function algorithmName(algorithm: unknown): unknown {
  return typeof algorithm === 'object' && algorithm !== null && 'name' in algorithm
    ? algorithm.name
    : algorithm
}

// generateKey est surchargée (paire ou clé seule, selon l'algorithme) : une doublure ne peut en reprendre toutes les
// signatures. L'assertion « as typeof generateKey » ne sert qu'à brancher la doublure ; elle délègue le reste au vrai
// generateKey, et seul l'algorithme visé est refusé.
function refuseAlgorithm(name: string) {
  const generateKey = crypto.subtle.generateKey.bind(crypto.subtle)
  vi.spyOn(crypto.subtle, 'generateKey').mockImplementation(((
    algorithm: Parameters<typeof generateKey>[0],
    extractable: boolean,
    usages: KeyUsage[],
  ) =>
    algorithmName(algorithm) === name
      ? Promise.reject(new DOMException('algorithme inconnu', 'NotSupportedError'))
      : generateKey(algorithm, extractable, usages)) as typeof generateKey)
}

describe('KEY-01 : contrôle des algorithmes au démarrage', () => {
  it('KEY-01 : Ed25519 et X25519 fonctionnent dans le WebCrypto de Node 24', async () => {
    await expect(checkCryptoSupport()).resolves.toBe(true)
  })

  it('KEY-01 : sans Ed25519, le navigateur n’est pas pris en charge', async () => {
    refuseAlgorithm('Ed25519')

    await expect(checkCryptoSupport()).resolves.toBe(false)
  })

  it('KEY-01 : sans X25519, le navigateur n’est pas pris en charge', async () => {
    refuseAlgorithm('X25519')

    await expect(checkCryptoSupport()).resolves.toBe(false)
  })

  it('KEY-01 : une signature Ed25519 qui ne se vérifie pas suffit à refuser le navigateur', async () => {
    vi.spyOn(crypto.subtle, 'verify').mockResolvedValue(false)

    await expect(checkCryptoSupport()).resolves.toBe(false)
  })

  it('KEY-01 : deux secrets X25519 différents des deux côtés suffisent à refuser le navigateur', async () => {
    let calls = 0
    vi.spyOn(crypto.subtle, 'deriveBits').mockImplementation(() =>
      Promise.resolve(new Uint8Array(32).fill(++calls).buffer),
    )

    await expect(checkCryptoSupport()).resolves.toBe(false)
  })

  it('KEY-01 : une clé seule rendue à la place d’une paire X25519 refuse le navigateur', async () => {
    const generateKey = crypto.subtle.generateKey.bind(crypto.subtle)
    const single = await crypto.subtle.generateKey({ name: 'AES-GCM', length: 256 }, false, [
      'encrypt',
    ])
    vi.spyOn(crypto.subtle, 'generateKey').mockImplementation(((
      algorithm: Parameters<typeof generateKey>[0],
      extractable: boolean,
      usages: KeyUsage[],
    ) =>
      algorithmName(algorithm) === 'X25519'
        ? Promise.resolve(single)
        : generateKey(algorithm, extractable, usages)) as typeof generateKey)

    await expect(checkCryptoSupport()).resolves.toBe(false)
  })
})
