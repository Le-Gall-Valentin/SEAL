/**
 * KEY-06 : le client vérifie le certificat reçu avant de l'enregistrer : signé par l'Autorité épinglée (KEY-11), pour
 * son compte, son identifiant et sa clé d'identité, avec l'usage et la validité de KEY-04 (5 minutes de retard
 * d'horloge tolérées, ADR 0027, point 9). C'est la seule voie qui
 * écrit un certificat dans le trousseau : aucune n'enregistre un certificat non vérifié (échec fermé).
 */
import { verifyEd25519 } from './ed25519'
import { encodeCertificate } from './identity'
import { type KeyStore, saveCertificate, type StoredCertificate } from './keystore'
import { fromBase64Url } from './base64url'
import { equalBytes } from './bytes'

const SERIAL_BYTES = 16
const KEY_ID_BYTES = 8
/** KEY-04 : notAfter = notBefore + 397 jours. */
const VALIDITY_MS = 397 * 86_400_000
/** Bit 0 : signature et non-répudiation ; bit 1 : certification de sous-clés. */
const USAGE = 0b11
/** ADR 0027, point 9 : retard d'horloge toléré, comme pour le createdAt d'une sous-clé (ADR 0025, point 7). */
const CLOCK_SKEW_MS = 5 * 60_000

/** Titulaire attendu : la session et le trousseau de l'appareil, jamais la réponse elle-même. */
export interface ExpectedOwner {
  readonly userId: string
  readonly username: string
  readonly ikPublic: Uint8Array
}

export type CertificateRejection =
  | 'encoding'
  | 'authority'
  | 'serial'
  | 'issuer'
  | 'signature'
  | 'subject'
  | 'username'
  | 'key'
  | 'usage'
  | 'validity'

export class CertificateRejectedError extends Error {
  override readonly name = 'CertificateRejectedError'
  readonly reason: CertificateRejection

  constructor(reason: CertificateRejection) {
    super(`certificat refusé : ${reason}`)
    this.reason = reason
  }
}

/**
 * Certificat tel que le serveur le sert (PUT /api/v1/keys/identity, GET /api/v1/users/{id}/keys) : octets en
 * base64url, dates en millisecondes Unix (l'adaptateur réseau convertit l'ISO 8601 ; ADR 0029).
 */
export interface ServedCertificate {
  readonly serial: string
  readonly issuerKeyId: string
  readonly subjectId: string
  readonly username: string
  readonly ikPublic: string
  readonly notBefore: number
  readonly notAfter: number
  readonly usage: number
  readonly signature: string
}

/** KEY-06 : vérifie le certificat reçu, sans rien enregistrer (ADR 0029, point 3 : il s'enregistre en dernier). */
export async function verifyOwnCertificate(
  store: KeyStore,
  served: ServedCertificate,
  expected: ExpectedOwner,
  now: number,
): Promise<void> {
  await verified(store, served, expected, now)
}

/** KEY-06 : vérifie le certificat reçu, puis enregistre ses seuls champs vérifiés. */
export async function storeOwnCertificate(
  store: KeyStore,
  served: ServedCertificate,
  expected: ExpectedOwner,
  now: number,
): Promise<void> {
  await saveCertificate(store, expected.userId, await verified(store, served, expected, now))
}

async function verified(
  store: KeyStore,
  served: ServedCertificate,
  expected: ExpectedOwner,
  now: number,
): Promise<StoredCertificate> {
  const certificate = decoded(served)
  const authority = await store.loadAuthority()
  if (authority === undefined) {
    throw new CertificateRejectedError('authority')
  }
  if (certificate.serial.length !== SERIAL_BYTES) {
    throw new CertificateRejectedError('serial')
  }
  const authorityDigest = new Uint8Array(await crypto.subtle.digest('SHA-256', authority))
  if (!equalBytes(certificate.issuerKeyId, authorityDigest.subarray(0, KEY_ID_BYTES))) {
    throw new CertificateRejectedError('issuer')
  }
  if (!(await signedBy(authority, certificate))) {
    throw new CertificateRejectedError('signature')
  }
  if (certificate.subjectId !== expected.userId) {
    throw new CertificateRejectedError('subject')
  }
  if (certificate.username !== expected.username) {
    throw new CertificateRejectedError('username')
  }
  if (!equalBytes(certificate.ikPublic, expected.ikPublic)) {
    throw new CertificateRejectedError('key')
  }
  if (certificate.usage !== USAGE) {
    throw new CertificateRejectedError('usage')
  }
  if (
    certificate.notAfter - certificate.notBefore !== VALIDITY_MS ||
    now < certificate.notBefore - CLOCK_SKEW_MS ||
    now >= certificate.notAfter
  ) {
    throw new CertificateRejectedError('validity')
  }
  return certificate
}

/**
 * Les neuf champs décodés une fois, en copies neuves : la vérification et l'enregistrement portent sur les mêmes
 * valeurs, et rien d'autre n'est enregistré. Un base64url non canonique est refusé ('encoding').
 */
function decoded(served: ServedCertificate): StoredCertificate {
  try {
    return {
      serial: fromBase64Url(served.serial),
      issuerKeyId: fromBase64Url(served.issuerKeyId),
      subjectId: served.subjectId,
      username: served.username,
      ikPublic: fromBase64Url(served.ikPublic),
      notBefore: served.notBefore,
      notAfter: served.notAfter,
      usage: served.usage,
      signature: fromBase64Url(served.signature),
    }
  } catch {
    throw new CertificateRejectedError('encoding')
  }
}

/** Des champs impossibles à encoder ne peuvent pas avoir été signés : ils valent une signature fausse. */
async function signedBy(authority: Uint8Array, received: StoredCertificate): Promise<boolean> {
  let encoded: Uint8Array
  try {
    encoded = encodeCertificate(received)
  } catch {
    return false
  }
  return verifyEd25519(authority, encoded, received.signature)
}
