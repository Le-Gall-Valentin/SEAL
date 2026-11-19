/**
 * Publication des clés du trousseau (KEY-03, KEY-05 ; PROTOCOL.md 4.3 et 4.4 ; ADR 0029) : IK signe la preuve de
 * possession et la liaison de sous-clé. Les deux fonctions rendent des types « fil », octets en base64url et dates en
 * millisecondes : rien n'en sort que des clés publiques et des signatures.
 */
import { fromBase64Url, toBase64Url } from './base64url'
import { equalBytes } from './bytes'
import { encodeProofOfPossession, encodeSubkeyBinding, type Kem } from './identity'
import { type Keyring, keyringSecrets } from './keyring'

const CHALLENGE_BYTES = 32
const SERIAL_BYTES = 16

/** Corps de PUT /api/v1/keys/identity (KEY-03). */
export interface IdentityProof {
  readonly ikPublic: string
  readonly challenge: string
  readonly signature: string
}

/** Liaison d'une sous-clé, telle que POST /api/v1/keys/subkeys la reçoit (KEY-05) ; dates en millisecondes Unix. */
export interface SubkeyBindingWire {
  readonly certSerial: string
  readonly ekId: string
  readonly kem: Kem
  readonly ekPublic: string
  readonly createdAt: number
  readonly expiresAt: number
  readonly signature: string
}

/** KEY-03 : preuve que le trousseau détient IK, sur le défi de 32 octets du serveur. */
export async function proveIdentity(keyring: Keyring, challenge: string): Promise<IdentityProof> {
  const bytes = fromBase64Url(challenge)
  if (bytes.length !== CHALLENGE_BYTES) {
    throw new RangeError('KEY-03 : un défi fait 32 octets')
  }
  const signed = encodeProofOfPossession({
    userId: keyring.userId,
    ikPublic: keyring.identityPublicKey,
    challenge: bytes,
  })
  return {
    ikPublic: toBase64Url(keyring.identityPublicKey),
    challenge: toBase64Url(bytes),
    signature: await signWithIdentity(keyring, signed),
  }
}

/** KEY-05 : liaison de la sous-clé `ekId` du trousseau au certificat `certSerial`, signée par IK. */
export async function bindSubkey(
  keyring: Keyring,
  certSerial: string,
  ekId: Uint8Array,
): Promise<SubkeyBindingWire> {
  const serial = fromBase64Url(certSerial)
  if (serial.length !== SERIAL_BYTES) {
    throw new RangeError('KEY-05 : un numéro de série fait 16 octets')
  }
  const subkey = keyring.subkeys.find((candidate) => equalBytes(candidate.ekId, ekId))
  if (subkey === undefined) {
    throw new TypeError('KEY-05 : cette sous-clé n’est pas dans le trousseau')
  }
  const signed = encodeSubkeyBinding({
    userId: keyring.userId,
    certSerial: serial,
    ekId: subkey.ekId,
    kem: subkey.kem,
    ekPublic: subkey.publicKey,
    createdAt: subkey.createdAt,
    expiresAt: subkey.expiresAt,
  })
  return {
    certSerial: toBase64Url(serial),
    ekId: toBase64Url(subkey.ekId),
    kem: subkey.kem,
    ekPublic: toBase64Url(subkey.publicKey),
    createdAt: subkey.createdAt,
    expiresAt: subkey.expiresAt,
    signature: await signWithIdentity(keyring, signed),
  }
}

async function signWithIdentity(keyring: Keyring, data: Uint8Array<ArrayBuffer>): Promise<string> {
  const signature = await crypto.subtle.sign('Ed25519', keyringSecrets(keyring).identityKey, data)
  return toBase64Url(new Uint8Array(signature))
}
