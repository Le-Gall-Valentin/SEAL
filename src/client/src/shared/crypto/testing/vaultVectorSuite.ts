/**
 * QA-02 : rejeu de vectors/vault.json (PROTOCOL.md, section 5 ; ADR 0027), commun aux tests sous Node (OpenSSL, comme le
 * générateur Python) et dans Chromium (BoringSSL, celui du client). Réservé aux tests.
 */
import { describe, expect, it } from 'vitest'
import { encodeCrockford } from '../crockford'
import { KEM } from '../identity'
import { type Keyring, keyringSecrets, openVault, serializeKeyring } from '../keyring'
import { parseRecoveryKey, type RecoveryKey, recoveryKeyBytes } from '../recovery'
import {
  kekAad,
  kekInfo,
  kekParameters,
  sealVault,
  type VaultPayload,
  VaultOpenError,
  vaultAad,
} from '../vault'
import { base64url, type Json, number, record, records, string } from './vectorReader'

export function vaultPayload(json: unknown): VaultPayload {
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

export function vectorRecoveryKey(vectors: Json): RecoveryKey {
  const groups = record(vectors['recoveryKey'])['groups']
  if (!Array.isArray(groups)) throw new Error('recoveryKey.groups : liste attendue')
  return parseRecoveryKey(groups.join('-'))
}

/** KEY-40 : aucune clé privée ni la VDK ne s'exporte, sous aucun format. */
export async function expectNothingExports(keyring: Keyring): Promise<void> {
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

export function describeVaultVectors(vectors: Json, engine: string): void {
  const userId = string(vectors, 'userId')
  const keyring = record(vectors['keyring'])
  const subkey = records(keyring['eks'])[0] ?? {}
  const publicKeys = record(vectors['publicKeys'])
  const recoveryKey = () => vectorRecoveryKey(vectors)

  describe(`KEY-31, QA-02 : coffre de clés, vecteurs de vault.json (${engine})`, () => {
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
      const vault = vaultPayload(vectors['payload'])
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
      const expected = vaultPayload(vectors['payload'])

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
      const opened = await openVault(userId, vaultPayload(vectors['payload']), recoveryKey())

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
      await expectNothingExports(
        await openVault(userId, vaultPayload(vectors['payload']), recoveryKey()),
      )
    })

    it('PROTOCOL.md 5.3 : une taille fausse ou une version impossible sont refusées avant tout calcul', async () => {
      const vault = vaultPayload(vectors['payload'])
      const shorter = (value: Uint8Array<ArrayBuffer>) => value.slice(1)

      for (const malformed of [
        { ...vault, salt: shorter(vault.salt) },
        { ...vault, nonceK: shorter(vault.nonceK) },
        { ...vault, nonceB: shorter(vault.nonceB) },
        { ...vault, wrappedVdk: shorter(vault.wrappedVdk) },
        ...[0, -1, 1.5, Number.NaN, 2 ** 53].map((vaultVersion) => ({ ...vault, vaultVersion })),
      ]) {
        await expect(openVault(userId, malformed, recoveryKey())).rejects.toMatchObject({
          reason: 'format',
        })
      }
    })

    const forgeries = records(vectors['forgeries'])
    it('QA-04 : les vecteurs comptent des falsifications, à chacun des trois étages', () => {
      expect(forgeries.length).toBeGreaterThanOrEqual(11)
      expect(new Set(forgeries.map((forgery) => string(forgery, 'rejectedBy')))).toEqual(
        new Set(['key', 'blob', 'keyring']),
      )
    })
    for (const forgery of forgeries) {
      it(`QA-04 : ${string(forgery, 'name')}, refusé à l’étage ${string(forgery, 'rejectedBy')}`, async () => {
        const key = parseRecoveryKey(encodeCrockford(base64url(forgery, 'recoveryKey')))

        const error: unknown = await openVault(
          string(forgery, 'userId'),
          vaultPayload(forgery['payload']),
          key,
        ).then(
          () => undefined,
          (thrown: unknown) => thrown,
        )

        expect(error).toBeInstanceOf(VaultOpenError)
        expect(error instanceof VaultOpenError ? error.reason : undefined).toBe(
          string(forgery, 'rejectedBy'),
        )
      })
    }
  })
}
