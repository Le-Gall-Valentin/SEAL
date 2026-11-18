/**
 * KEY-06 : le client vérifie le certificat reçu avant de l'enregistrer : signé par l'Autorité épinglée (KEY-11), pour
 * son compte, son identifiant et sa clé d'identité, avec l'usage et la validité de KEY-04. C'est la seule voie qui
 * écrit un certificat dans le trousseau : aucune n'enregistre un certificat non vérifié (échec fermé).
 */
import { verifyEd25519 } from './ed25519'
import { encodeCertificate } from './identity'
import { type KeyStore, saveCertificate, type StoredCertificate } from './keystore'

const SERIAL_BYTES = 16
const KEY_ID_BYTES = 8
/** KEY-04 : notAfter = notBefore + 397 jours. */
const VALIDITY_MS = 397 * 86_400_000
/** Bit 0 : signature et non-répudiation ; bit 1 : certification de sous-clés. */
const USAGE = 0b11

/** Titulaire attendu : la session et le trousseau de l'appareil, jamais la réponse elle-même. */
export interface ExpectedOwner {
  readonly userId: string
  readonly username: string
  readonly ikPublic: Uint8Array
}

export type CertificateRejection =
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

export async function storeOwnCertificate(
  store: KeyStore,
  received: StoredCertificate,
  expected: ExpectedOwner,
  now: number,
): Promise<void> {
  const authority = await store.loadAuthority()
  if (authority === undefined) {
    throw new CertificateRejectedError('authority')
  }
  if (received.serial.length !== SERIAL_BYTES) {
    throw new CertificateRejectedError('serial')
  }
  const authorityDigest = new Uint8Array(await crypto.subtle.digest('SHA-256', authority))
  if (!equal(received.issuerKeyId, authorityDigest.subarray(0, KEY_ID_BYTES))) {
    throw new CertificateRejectedError('issuer')
  }
  if (!(await signedBy(authority, received))) {
    throw new CertificateRejectedError('signature')
  }
  if (received.subjectId !== expected.userId) {
    throw new CertificateRejectedError('subject')
  }
  if (received.username !== expected.username) {
    throw new CertificateRejectedError('username')
  }
  if (!equal(received.ikPublic, expected.ikPublic)) {
    throw new CertificateRejectedError('key')
  }
  if (received.usage !== USAGE) {
    throw new CertificateRejectedError('usage')
  }
  if (
    received.notAfter - received.notBefore !== VALIDITY_MS ||
    now < received.notBefore ||
    now >= received.notAfter
  ) {
    throw new CertificateRejectedError('validity')
  }
  await saveCertificate(store, expected.userId, received)
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

function equal(left: Uint8Array, right: Uint8Array): boolean {
  return left.length === right.length && left.every((byte, index) => byte === right[index])
}
