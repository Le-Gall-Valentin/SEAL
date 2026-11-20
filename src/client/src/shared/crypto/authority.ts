/**
 * KEY-11 : le client épingle la clé de l'Autorité au premier usage et bloque l'application si elle change. La réponse
 * de GET /api/v1/pki/ca n'est crue que cohérente : clé de 32 octets ni d'ordre faible ni non canonique, keyId et
 * empreinte qui en dérivent (PROTOCOL.md 4.1). Une réponse incohérente n'est jamais épinglée (ADR 0027, point 5).
 */
import { fromBase64Url } from './base64url'
import { isWeakOrNonCanonical } from './ed25519'
import { type KeyStore, pinAuthority } from './keystore'
import { equalBytes } from './bytes'

const PUBLIC_KEY_BYTES = 32
const KEY_ID_BYTES = 8

/** Réponse de GET /api/v1/pki/ca, telle que servie : octets en base64url, empreinte en hexadécimal majuscule. */
export interface ServedAuthority {
  readonly publicKey: string
  readonly keyId: string
  readonly fingerprint: string
}

/** pinned : épinglée à l'instant ; unchanged : la même ; changed et invalid : l'application se bloque. */
export type AuthorityCheck = 'pinned' | 'unchanged' | 'changed' | 'invalid'

export async function checkAuthority(
  store: KeyStore,
  served: ServedAuthority,
): Promise<AuthorityCheck> {
  const publicKey = await coherentKey(served)
  if (publicKey === undefined) {
    return 'invalid'
  }
  const pinned = await store.loadAuthority()
  if (pinned !== undefined) {
    return equalBytes(pinned, publicKey) ? 'unchanged' : 'changed'
  }
  // Un autre onglet a pu épingler entre la lecture et l'écriture : la clé rendue est celle qui reste.
  const kept = await pinAuthority(store, publicKey)
  return equalBytes(kept, publicKey) ? 'pinned' : 'changed'
}

async function coherentKey(served: ServedAuthority): Promise<Uint8Array<ArrayBuffer> | undefined> {
  let publicKey: Uint8Array<ArrayBuffer>
  let keyId: Uint8Array<ArrayBuffer>
  try {
    publicKey = fromBase64Url(served.publicKey)
    keyId = fromBase64Url(served.keyId)
  } catch {
    return undefined
  }
  if (publicKey.length !== PUBLIC_KEY_BYTES || isWeakOrNonCanonical(publicKey)) {
    return undefined
  }
  const digest = new Uint8Array(await crypto.subtle.digest('SHA-256', publicKey))
  if (!equalBytes(digest.subarray(0, KEY_ID_BYTES), keyId) || hex(digest) !== served.fingerprint) {
    return undefined
  }
  return publicKey
}

function hex(bytes: Uint8Array): string {
  return Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0'))
    .join('')
    .toUpperCase()
}
