import { readFileSync } from 'node:fs'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { encodeCrockford } from './crockford'
import { KEM } from './identity'
import {
  generateKeyring,
  type Keyring,
  keyringSecrets,
  openVault,
  serializeKeyring,
} from './keyring'
import { parseRecoveryKey, recoveryKeyBytes } from './recovery'
import { base64url, number, record, records, string } from './testing/vectorReader'
import {
  kekAad,
  kekInfo,
  kekParameters,
  sealVault,
  type VaultPayload,
  VaultOpenError,
  vaultAad,
} from './vault'

// QA-02 : vecteurs produits par src/protocol/tools/gen_vectors.py (PROTOCOL.md, section 5 ; ADR 0027).
const VAULT = new URL('../../../../protocol/vectors/vault.json', import.meta.url)
const vectors = record(JSON.parse(readFileSync(VAULT, 'utf8')))
const userId = string(vectors, 'userId')
const keyring = record(vectors['keyring'])
const subkey = records(keyring['eks'])[0] ?? {}
const publicKeys = record(vectors['publicKeys'])
const DAY_MS = 86_400_000

afterEach(() => {
  vi.restoreAllMocks()
})

function payload(json: unknown): VaultPayload {
  const fields = record(json)
  return {
    vaultVersion: number(fields, 'vaultVersion'),
    salt: base64url(fields, 'salt'),
    nonceK: base64url(fields, 'nonceK'),
    wrappedVdk: base64url(fields, 'wrappedVdk'),
    nonceB: base64url(fields, 'nonceB'),
    blob: base64url(fields, 'blob'),
  }
}

function recoveryKey() {
  const groups = record(vectors['recoveryKey'])['groups']
  if (!Array.isArray(groups)) throw new Error('recoveryKey.groups : liste attendue')
  return parseRecoveryKey(groups.join('-'))
}

/** KEY-40 : aucune clé privée ni la VDK ne s'exporte, sous aucun format. */
async function expectNothingExports(keyring: Keyring): Promise<void> {
  const secrets = keyringSecrets(keyring)
  const subkeyKey = secrets.subkeyKeys[0]
  if (subkeyKey === undefined || secrets.subkeyKeys.length !== keyring.subkeys.length) {
    throw new Error('une clé privée par sous-clé attendue')
  }
  for (const [format, key] of [
    ['pkcs8', secrets.identityKey],
    ['pkcs8', subkeyKey],
    ['raw', secrets.vdk],
  ] as const) {
    expect(key.extractable).toBe(false)
    await expect(crypto.subtle.exportKey(format, key)).rejects.toThrow()
  }
}

describe('KEY-31, QA-02 : coffre de clés, vecteurs de vault.json', () => {
  it('KEY-30 : la clé de récupération des vecteurs se relit en ses 20 octets', () => {
    expect(recoveryKeyBytes(recoveryKey())).toEqual(
      base64url(record(vectors['recoveryKey']), 'bytes'),
    )
  })

  it('QA-02 : les structures de l’AAD et de l’info HKDF sont encodées à l’octet près', () => {
    expect(vaultAad(userId, 1)).toEqual(base64url(vectors, 'vaultAad'))
    expect(kekInfo(userId)).toEqual(base64url(vectors, 'kekInfo'))
    expect(kekAad(userId)).toEqual(base64url(vectors, 'kekAad'))
  })

  it('KEY-31 : la KEK est HKDF-SHA256 de RK, avec le sel et l’info du coffre', async () => {
    const vault = payload(vectors['payload'])
    const ikm = await crypto.subtle.importKey(
      'raw',
      recoveryKeyBytes(recoveryKey()),
      'HKDF',
      false,
      ['deriveBits'],
    )
    const kek = await crypto.subtle.deriveBits(kekParameters(vault.salt, userId), ikm, 256)

    expect(new Uint8Array(kek)).toEqual(base64url(vectors, 'kek'))
  })

  it('QA-02 : le coffre scellé avec le sel et les nonces des vecteurs est identique à l’octet près', async () => {
    const expected = payload(vectors['payload'])

    const sealed = await sealVault({
      userId,
      vaultVersion: 1,
      recoveryKey: recoveryKey(),
      vdk: base64url(vectors, 'vdk'),
      plaintext: base64url(vectors, 'keyringBytes'),
      nonces: { salt: expected.salt, nonceK: expected.nonceK, nonceB: expected.nonceB },
    })

    expect(sealed).toEqual(expected)
  })

  it('QA-02 : le trousseau sérialisé par le client est celui des vecteurs, à l’octet près', () => {
    const plaintext = serializeKeyring({
      userId,
      ik: base64url(keyring, 'ik'),
      eks: [
        {
          ekId: base64url(subkey, 'ekId'),
          kem: KEM,
          pkcs8: base64url(subkey, 'pkcs8'),
          createdAt: number(subkey, 'createdAt'),
          expiresAt: number(subkey, 'expiresAt'),
        },
      ],
      createdAt: number(keyring, 'createdAt'),
    })

    expect(plaintext).toEqual(base64url(vectors, 'keyringBytes'))
  })

  it('KEY-33 : le coffre des vecteurs s’ouvre avec RK, et rend les clés publiques et les dates attendues', async () => {
    const opened = await openVault(userId, payload(vectors['payload']), recoveryKey())

    expect(opened.userId).toBe(userId)
    expect(opened.identityPublicKey).toEqual(base64url(publicKeys, 'ik'))
    expect(opened.subkeys).toHaveLength(1)
    expect(opened.subkeys[0]).toMatchObject({
      ekId: base64url(subkey, 'ekId'),
      kem: KEM,
      publicKey: base64url(publicKeys, 'ek'),
      createdAt: number(subkey, 'createdAt'),
      expiresAt: number(subkey, 'expiresAt'),
    })
    expect(opened.createdAt).toBe(number(keyring, 'createdAt'))
  })

  it('KEY-40 : aucune clé du trousseau ouvert ne s’exporte', async () => {
    const opened = await openVault(userId, payload(vectors['payload']), recoveryKey())

    await expectNothingExports(opened)
  })

  it('PROTOCOL.md 5.3 : un sel, un nonce ou une VDK enveloppée d’une autre taille sont refusés avant tout calcul', async () => {
    const vault = payload(vectors['payload'])
    const shorter = (value: Uint8Array<ArrayBuffer>) => value.slice(1)

    for (const malformed of [
      { ...vault, salt: shorter(vault.salt) },
      { ...vault, nonceK: shorter(vault.nonceK) },
      { ...vault, nonceB: shorter(vault.nonceB) },
      { ...vault, wrappedVdk: shorter(vault.wrappedVdk) },
    ]) {
      await expect(openVault(userId, malformed, recoveryKey())).rejects.toBeInstanceOf(
        VaultOpenError,
      )
    }
  })

  it('KEY-33 : un JWK sans clé publique, d’un WebCrypto défaillant, refuse le trousseau', async () => {
    vi.spyOn(crypto.subtle, 'exportKey').mockResolvedValueOnce({ kty: 'OKP', crv: 'Ed25519' })

    await expect(
      openVault(userId, payload(vectors['payload']), recoveryKey()),
    ).rejects.toBeInstanceOf(VaultOpenError)
  })

  it('KEY-40 : une VDK d’une autre taille que 32 octets ne se scelle pas', async () => {
    await expect(
      sealVault({
        userId,
        vaultVersion: 1,
        recoveryKey: recoveryKey(),
        vdk: new Uint8Array(16),
        plaintext: new Uint8Array(1),
      }),
    ).rejects.toThrow(RangeError)
  })

  const falsifications = records(vectors['falsifications'])
  it('QA-04 : les vecteurs comptent des falsifications', () => {
    expect(falsifications.length).toBeGreaterThanOrEqual(9)
  })
  for (const falsification of falsifications) {
    it(`QA-04 : ${string(falsification, 'name')}, refusé sans trousseau partiel`, async () => {
      const key = parseRecoveryKey(encodeCrockford(base64url(falsification, 'recoveryKey')))

      await expect(
        openVault(string(falsification, 'userId'), payload(falsification['payload']), key),
      ).rejects.toBeInstanceOf(VaultOpenError)
    })
  }
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
