import { readFileSync } from 'node:fs'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { createKeyring, generateKeyring, keyringSecrets, openVault } from './keyring'
import { recoveryKeyBytes } from './recovery'
import {
  describeVaultVectors,
  expectNothingExports,
  vaultPayload,
  vectorRecoveryKey,
} from './testing/vaultVectorSuite'
import { record, string } from './testing/vectorReader'
import { sealVault, VaultOpenError } from './vault'

// QA-02 : vecteurs produits par src/protocol/tools/gen_vectors.py (PROTOCOL.md, section 5 ; ADR 0027).
const VAULT = new URL('../../../../protocol/vectors/vault.json', import.meta.url)
const vectors = record(JSON.parse(readFileSync(VAULT, 'utf8')))
const userId = string(vectors, 'userId')
const DAY_MS = 86_400_000

afterEach(() => {
  vi.restoreAllMocks()
})

describeVaultVectors(vectors, 'Node')

describe('KEY-33, KEY-40 : ouverture et scellement, cas propres à Node', () => {
  it('KEY-33 : un JWK sans clé publique, d’un WebCrypto défaillant, refuse le trousseau', async () => {
    vi.spyOn(crypto.subtle, 'exportKey').mockResolvedValueOnce({ kty: 'OKP', crv: 'Ed25519' })

    await expect(
      openVault(userId, vaultPayload(vectors['payload']), vectorRecoveryKey(vectors)),
    ).rejects.toMatchObject({ reason: 'keyring' })
  })

  it('KEY-40 : une VDK d’une autre taille que 32 octets ne se scelle pas', async () => {
    await expect(
      sealVault({
        userId,
        vaultVersion: 1,
        recoveryKey: vectorRecoveryKey(vectors),
        vdk: new Uint8Array(16),
        plaintext: new Uint8Array(1),
      }),
    ).rejects.toThrow(RangeError)
  })

  it('PROTOCOL.md 5.3 : VaultOpenError ne porte que son étage, jamais de secret', () => {
    const error = new VaultOpenError('key')

    expect(error.reason).toBe('key')
    expect(error.message).toBe('coffre refusé : key')
  })
})

describe('KEY-02, KEY-40 : génération du trousseau', () => {
  const NOW = 1_794_009_600_000

  it('KEY-40 : IK, EK et la VDK générées s’ouvrent par RK, et aucune ne s’exporte', async () => {
    const generated = await generateKeyring(userId, NOW)
    const opened = await openVault(userId, generated.vault, generated.recoveryKey)
    const message = new TextEncoder().encode('SEAL')
    const signature = await crypto.subtle.sign(
      'Ed25519',
      keyringSecrets(generated.keyring).identityKey,
      message,
    )
    const publicKey = await crypto.subtle.importKey(
      'raw',
      opened.identityPublicKey,
      'Ed25519',
      false,
      ['verify'],
    )

    await expect(crypto.subtle.verify('Ed25519', publicKey, signature, message)).resolves.toBe(true)
    expect(opened.identityPublicKey).toEqual(generated.keyring.identityPublicKey)
    expect(opened.subkeys[0]?.publicKey).toEqual(generated.keyring.subkeys[0]?.publicKey)
    await expectNothingExports(generated.keyring)
    await expectNothingExports(opened)
  })

  it('ADR 0027, point 12 : le trousseau ne montre que des données publiques, et un faux n’a pas de clés', async () => {
    const { keyring: created } = await generateKeyring(userId, NOW)
    const values = [
      ...Object.values<unknown>({ ...created }),
      ...created.subkeys.flatMap((subkey) => Object.values<unknown>({ ...subkey })),
    ]

    expect(values.some((value) => value instanceof CryptoKey)).toBe(false)
    expect(Object.isFrozen(created)).toBe(true)
    expect(() => keyringSecrets({ ...created })).toThrow(TypeError)
    expect(() => createKeyring(created, { ...keyringSecrets(created), subkeyKeys: [] })).toThrow(
      TypeError,
    )
  })

  it('KEY-21, KEY-32 : une sous-clé de 45 jours, un ekId de 16 octets, la version 1 et les tailles du serveur', async () => {
    const { keyring: created, vault } = await generateKeyring(userId, NOW)

    expect(created.subkeys).toHaveLength(1)
    expect(created.subkeys[0]?.ekId).toHaveLength(16)
    expect(created.subkeys[0]?.createdAt).toBe(NOW)
    expect(created.subkeys[0]?.expiresAt).toBe(NOW + 45 * DAY_MS)
    expect(created.createdAt).toBe(NOW)
    expect(vault.vaultVersion).toBe(1)
    expect(vault.salt).toHaveLength(32)
    expect(vault.nonceK).toHaveLength(12)
    expect(vault.nonceB).toHaveLength(12)
    expect(vault.wrappedVdk).toHaveLength(48)
  })

  it('KEY-30 : deux trousseaux n’ont ni la même RK, ni le même sel, ni les mêmes nonces', async () => {
    const first = await generateKeyring(userId, NOW)
    const second = await generateKeyring(userId, NOW)

    expect(recoveryKeyBytes(first.recoveryKey)).not.toEqual(recoveryKeyBytes(second.recoveryKey))
    expect(first.vault.salt).not.toEqual(second.vault.salt)
    expect(first.vault.nonceB).not.toEqual(second.vault.nonceB)
    expect(first.keyring.subkeys[0]?.ekId).not.toEqual(second.keyring.subkeys[0]?.ekId)
  })

  it('KEY-31 : un coffre généré ne s’ouvre ni pour un autre compte, ni avec une autre RK', async () => {
    const { vault } = await generateKeyring(userId, NOW)
    const other = await generateKeyring(userId, NOW)

    await expect(
      openVault('0192f7a1-ffff-7000-8000-000000000b0b', vault, other.recoveryKey),
    ).rejects.toBeInstanceOf(VaultOpenError)
    await expect(openVault(userId, vault, other.recoveryKey)).rejects.toBeInstanceOf(VaultOpenError)
  })
})
