import { readFileSync } from 'node:fs'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { createKeyring, generateKeyring, keyringSecrets, openVault } from './keyring'
import { generateRecoveryKey } from './recovery'
import {
  describeVaultVectors,
  expectNothingExports,
  vaultPayload,
  vectorRecoveryKey,
} from './testing/vaultVectorSuite'
import { base64url, record, string } from './testing/vectorReader'
import { sealVault, VaultOpenError, vaultFromWire, vaultToWire, type VaultWire } from './vault'

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
    const recoveryKey = generateRecoveryKey()
    const generated = await generateKeyring(userId, recoveryKey, NOW)
    const opened = await openVault(userId, generated.vault, recoveryKey)
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
    const { keyring: created } = await generateKeyring(userId, generateRecoveryKey(), NOW)
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
    const { keyring: created, vault } = await generateKeyring(userId, generateRecoveryKey(), NOW)

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

  it('KEY-30 : deux trousseaux, même sous la même RK, n’ont ni le même sel, ni les mêmes nonces, ni le même ekId', async () => {
    const recoveryKey = generateRecoveryKey()
    const first = await generateKeyring(userId, recoveryKey, NOW)
    const second = await generateKeyring(userId, recoveryKey, NOW)

    expect(first.vault.salt).not.toEqual(second.vault.salt)
    expect(first.vault.nonceB).not.toEqual(second.vault.nonceB)
    expect(first.keyring.subkeys[0]?.ekId).not.toEqual(second.keyring.subkeys[0]?.ekId)
  })

  it('KEY-31 : un coffre généré ne s’ouvre ni pour un autre compte, ni avec une autre RK', async () => {
    const recoveryKey = generateRecoveryKey()
    const { vault } = await generateKeyring(userId, recoveryKey, NOW)
    const otherKey = generateRecoveryKey()

    await expect(
      openVault('0192f7a1-ffff-7000-8000-000000000b0b', vault, recoveryKey),
    ).rejects.toBeInstanceOf(VaultOpenError)
    await expect(openVault(userId, vault, otherKey)).rejects.toBeInstanceOf(VaultOpenError)
  })
})

describe('KEY-33, KEY-40 : coffres piégés, repris de la revue de sécurité', () => {
  const keyringJson = new TextDecoder().decode(base64url(vectors, 'keyringBytes'))
  const OTHER_ID = '0192f7a1-ffff-7000-8000-000000000b0b'

  /** Scelle un trousseau en clair arbitraire avec la RK et la VDK des vecteurs, comme le ferait un client fautif. */
  async function sealed(json: string, vaultVersion = 1) {
    return sealVault({
      userId,
      vaultVersion,
      recoveryKey: vectorRecoveryKey(vectors),
      vdk: base64url(vectors, 'vdk'),
      plaintext: new TextEncoder().encode(json),
    })
  }

  it('KEY-40 : un JSON piégé est refusé à la relecture, sans pollution de prototype', async () => {
    const traps = [
      `{"__proto__":{"polluted":true},${keyringJson.slice(1)}`,
      `{"constructor":{"prototype":{"polluted":true}},${keyringJson.slice(1)}`,
      keyringJson.replace(`"userId":"${userId}"`, `"userId":"${userId}","userId":"${OTHER_ID}"`),
      keyringJson.replace(`"userId":"${userId}"`, `"userId":"${userId.toUpperCase()}"`),
      keyringJson.replace(/"createdAt":\d+}$/, '"createdAt":1e308}'),
      keyringJson.replace('"v":1', '"v":"1"'),
    ]
    for (const trap of traps) {
      expect(trap, 'piège construit').not.toBe(keyringJson)
      await expect(
        openVault(userId, await sealed(trap), vectorRecoveryKey(vectors)),
      ).rejects.toMatchObject({
        reason: 'keyring',
      })
    }
    expect(Object.prototype).not.toHaveProperty('polluted')
  })

  it('ADR 0027, point 10 : une ancienne version authentique s’ouvre encore : restauration et reprise la refusent (PROTOCOL.md 5.3)', async () => {
    const first = await sealed(keyringJson, 1)
    const second = await sealed(keyringJson, 2)

    await expect(openVault(userId, first, vectorRecoveryKey(vectors))).resolves.toBeDefined()
    await expect(openVault(userId, second, vectorRecoveryKey(vectors))).resolves.toBeDefined()
  })
})

describe('ADR 0029 : le coffre au format de l’API', () => {
  it('KEY-32 : le coffre des vecteurs passe en base64url à l’identique, et revient octet pour octet', () => {
    const payload = vaultPayload(vectors['payload'])
    const wire: VaultWire = vaultToWire(payload)

    expect(wire).toEqual(vectors['payload'])
    expect(vaultFromWire(wire)).toEqual(payload)
  })

  it('KEY-33 : un base64url non canonique refuse le coffre à l’étage format', () => {
    const wire = vaultToWire(vaultPayload(vectors['payload']))

    expect(() => vaultFromWire({ ...wire, salt: `${wire.salt}=` })).toThrow(VaultOpenError)
    expect(() => vaultFromWire({ ...wire, blob: '*' })).toThrow(
      expect.objectContaining({ reason: 'format' }),
    )
  })
})
