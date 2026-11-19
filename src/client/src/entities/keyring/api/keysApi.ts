import { z } from 'zod'
import { request, requestNoContent } from '@/shared/api'
import { ApiError } from '@/shared/lib'
import type { IKeysApi, SubkeyBody } from './IKeysApi'

/** Base64url sans remplissage d'exactement `bytes` octets ; shared/crypto en vérifie ensuite la forme canonique. */
const base64url = (bytes: number) =>
  z.string().regex(new RegExp(`^[A-Za-z0-9_-]{${String(Math.ceil((bytes * 4) / 3))}}$`))

/**
 * ISO 8601 en UTC, à la milliseconde au plus, converti en millisecondes Unix : les structures signées portent des
 * millisecondes, et une précision plus fine ne se retrouverait pas dans la signature.
 */
const isoMillis = z
  .string()
  .regex(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d{1,3})?Z$/)
  .transform((text) => Date.parse(text))
  .pipe(z.number().int().nonnegative().max(Number.MAX_SAFE_INTEGER))

const KEM = z.literal('DHKEM-X25519-HKDF-SHA256')

/** SEC-01 : réponses strictes ; tailles de PROTOCOL.md 4.2, 4.4 et 5.3, et de l'ADR 0025 (point 6). */
const CHALLENGE = z.strictObject({ challenge: base64url(32), expiresAt: isoMillis })

const CERTIFICATE = z.strictObject({
  serial: base64url(16),
  issuerKeyId: base64url(8),
  subjectId: z.uuid(),
  username: z.string().min(3).max(20),
  ikPublic: base64url(32),
  notBefore: isoMillis,
  notAfter: isoMillis,
  usage: z.number().int().min(0).max(255),
  signature: base64url(64),
})

const SUBKEY = z.strictObject({
  ekId: base64url(16),
  kem: KEM,
  ekPublic: base64url(32),
  createdAt: isoMillis,
  expiresAt: isoMillis,
  signature: base64url(64),
})

/** ADR 0025, point 15 : trois sous-clés vivantes par certificat ; seize au plus bornent la réponse. */
const OWN_KEYS = z.strictObject({ certificate: CERTIFICATE, subkeys: z.array(SUBKEY).max(16) })

/** ADR 0025, point 6 : blob de 16 octets à 32 Kio. */
const VAULT = z.strictObject({
  vaultVersion: z.number().int().min(1).max(Number.MAX_SAFE_INTEGER),
  salt: base64url(32),
  nonceK: base64url(12),
  wrappedVdk: base64url(48),
  nonceB: base64url(12),
  blob: z
    .string()
    .min(22)
    .max(43_691)
    .regex(/^[A-Za-z0-9_-]+$/),
})

/** Rend `undefined` pour l'erreur attendue (404 KEY_NOT_FOUND) ; toute autre erreur remonte telle quelle. */
async function unlessNotFound<T>(pending: Promise<T>): Promise<T | undefined> {
  try {
    return await pending
  } catch (error) {
    if (error instanceof ApiError && error.status === 404 && error.code === 'KEY_NOT_FOUND') {
      return undefined
    }
    throw error
  }
}

function subkeyBody(binding: SubkeyBody) {
  return {
    certSerial: binding.certSerial,
    ekId: binding.ekId,
    kem: binding.kem,
    ekPublic: binding.ekPublic,
    createdAt: new Date(binding.createdAt).toISOString(),
    expiresAt: new Date(binding.expiresAt).toISOString(),
    signature: binding.signature,
  }
}

/** Adaptateur HTTP du port IKeysApi (CDC 14.3 ; ADR 0025, point 5 ; ADR 0029, point 4). */
export const keysApi: IKeysApi = {
  async createChallenge() {
    return (await request('/api/v1/keys/challenge', CHALLENGE, { method: 'POST' })).challenge
  },
  publishIdentity: (proof) =>
    request('/api/v1/keys/identity', CERTIFICATE, { method: 'PUT', body: proof }),
  publishSubkey: (binding) =>
    requestNoContent('/api/v1/keys/subkeys', { method: 'POST', body: subkeyBody(binding) }),
  readVault: () => unlessNotFound(request('/api/v1/keys/vault', VAULT)),
  async saveVault(vault) {
    try {
      await requestNoContent('/api/v1/keys/vault', { method: 'PUT', body: vault })
      return 'saved'
    } catch (error) {
      if (
        error instanceof ApiError &&
        error.status === 409 &&
        error.code === 'VAULT_VERSION_CONFLICT'
      ) {
        return 'conflict'
      }
      throw error
    }
  },
  readOwnKeys: (userId) =>
    unlessNotFound(request(`/api/v1/users/${encodeURIComponent(userId)}/keys`, OWN_KEYS)),
}
