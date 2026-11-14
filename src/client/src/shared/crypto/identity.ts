/**
 * Structures signées de l'identité (CDC sections 6.1 à 6.3, PROTOCOL.md section 4 ; KEY-03 à KEY-05, ADR 0025) :
 * certificat SEAL, preuve de possession, liaison de sous-clé. Octets produits par l'encodeur canonique, jamais par
 * JSON.stringify. Chaque étiquette fixe aussi l'algorithme de signature, Ed25519.
 */
import { bytes, struct, text, u64, u8, uuid } from './canonical'

/** Champs du certificat d'identité (section 4.2) ; dates en millisecondes Unix. */
export interface CertificateFields {
  readonly serial: Uint8Array
  readonly issuerKeyId: Uint8Array
  readonly subjectId: string
  readonly username: string
  readonly ikPublic: Uint8Array
  readonly notBefore: number
  readonly notAfter: number
  readonly usage: number
}

/** Champs de la preuve de possession (section 4.3). */
export interface ProofOfPossessionFields {
  readonly userId: string
  readonly ikPublic: Uint8Array
  readonly challenge: Uint8Array
}

/** Champs de la liaison de sous-clé (section 4.4) ; dates en millisecondes Unix. */
export interface SubkeyBindingFields {
  readonly userId: string
  readonly certSerial: Uint8Array
  readonly ekId: Uint8Array
  readonly kem: string
  readonly ekPublic: Uint8Array
  readonly createdAt: number
  readonly expiresAt: number
}

/** KEY-04 : octets signés par la CA. */
export function encodeCertificate(fields: CertificateFields): Uint8Array<ArrayBuffer> {
  return struct(
    'SEAL-cert-v1',
    bytes(fields.serial),
    bytes(fields.issuerKeyId),
    uuid(fields.subjectId),
    text(fields.username),
    bytes(fields.ikPublic),
    u64(fields.notBefore),
    u64(fields.notAfter),
    u8(fields.usage),
  )
}

/** KEY-03 : octets signés par IK pour prouver sa possession. */
export function encodeProofOfPossession(fields: ProofOfPossessionFields): Uint8Array<ArrayBuffer> {
  return struct('SEAL-pop-v1', uuid(fields.userId), bytes(fields.ikPublic), bytes(fields.challenge))
}

/** KEY-05 : octets signés par IK pour lier une sous-clé X25519. */
export function encodeSubkeyBinding(fields: SubkeyBindingFields): Uint8Array<ArrayBuffer> {
  return struct(
    'SEAL-subkey-v1',
    uuid(fields.userId),
    bytes(fields.certSerial),
    bytes(fields.ekId),
    text(fields.kem),
    bytes(fields.ekPublic),
    u64(fields.createdAt),
    u64(fields.expiresAt),
  )
}
