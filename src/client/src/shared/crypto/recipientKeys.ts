/**
 * KEY-20, PROTOCOL.md 6.3 (ADR 0030, point 5) : un émetteur utilise toujours la sous-clé non expirée la plus récente
 * du destinataire, après avoir vérifié le certificat qui la porte et sa liaison. Un certificat ou une seule liaison
 * invalides refusent toutes les clés du destinataire : une signature fausse n'est jamais normale, c'est un serveur qui
 * ment.
 */
import { fromBase64Url } from './base64url'
import { CertificateRejectedError, type ServedCertificate, verifyCertificate } from './certificate'
import { verifyEd25519 } from './ed25519'
import { encodeSubkeyBinding, KEM } from './identity'
import type { KeyStore } from './keystore'
import { CLOCK_SKEW_MS, EK_ID_BYTES, SUBKEY_VALIDITY_MS } from './protocol'

const EK_PUBLIC_BYTES = 32

/** Sous-clé telle que la sert GET /api/v1/users/{id}/keys : sans `certSerial`, porté par le certificat (4.4). */
export interface ServedSubkey {
  readonly ekId: string
  readonly kem: string
  readonly ekPublic: string
  readonly createdAt: number
  readonly expiresAt: number
  readonly signature: string
}

/** Réponse de GET /api/v1/users/{id}/keys : octets en base64url, dates en millisecondes. */
export interface ServedUserKeys {
  readonly certificate: ServedCertificate
  readonly subkeys: readonly ServedSubkey[]
}

/** Sous-clé retenue pour un destinataire, avec le certificat vérifié qui la porte. */
export interface ChosenSubkey {
  readonly recipientId: string
  readonly certSerial: Uint8Array<ArrayBuffer>
  readonly ikPublic: Uint8Array<ArrayBuffer>
  readonly ekId: Uint8Array<ArrayBuffer>
  readonly ekPublic: Uint8Array<ArrayBuffer>
}

export type SubkeyUnavailableReason = 'certificate' | 'binding' | 'none'

/** CDC 7.5, étape 2 : « clé indisponible » ; l'envoi est annulé. */
export class SubkeyUnavailableError extends Error {
  override readonly name = 'SubkeyUnavailableError'
  readonly reason: SubkeyUnavailableReason

  constructor(reason: SubkeyUnavailableReason, options?: ErrorOptions) {
    super(`clé indisponible : ${reason}`, options)
    this.reason = reason
  }
}

interface VerifiedSubkey {
  readonly ekId: Uint8Array<ArrayBuffer>
  readonly ekPublic: Uint8Array<ArrayBuffer>
  readonly createdAt: number
  readonly expiresAt: number
}

/** KEY-20 : la sous-clé du destinataire `recipientId` à utiliser à l'instant `now`. */
export async function chooseSubkey(
  store: KeyStore,
  served: ServedUserKeys,
  recipientId: string,
  now: number,
): Promise<ChosenSubkey> {
  let certificate
  try {
    certificate = await verifyCertificate(
      store,
      served.certificate,
      recipientId,
      now,
      CLOCK_SKEW_MS,
    )
  } catch (error) {
    if (error instanceof CertificateRejectedError) {
      throw new SubkeyUnavailableError('certificate', { cause: error })
    }
    throw error
  }
  const subkeys = await Promise.all(
    served.subkeys.map((subkey) =>
      verifiedSubkey(subkey, recipientId, certificate.serial, certificate.ikPublic, now),
    ),
  )
  const newest = subkeys.reduce<VerifiedSubkey | undefined>((latest, subkey) => {
    if (subkey === undefined) {
      throw new SubkeyUnavailableError('binding')
    }
    return subkey.expiresAt > now && (latest === undefined || subkey.createdAt > latest.createdAt)
      ? subkey
      : latest
  }, undefined)
  if (newest === undefined) {
    throw new SubkeyUnavailableError('none')
  }
  return {
    recipientId,
    certSerial: Uint8Array.from(certificate.serial),
    ikPublic: Uint8Array.from(certificate.ikPublic),
    ekId: newest.ekId,
    ekPublic: newest.ekPublic,
  }
}

/**
 * PROTOCOL.md 4.4 : liaison signée par l'IK du certificat, avec son numéro de série ; undefined si elle ne l'est pas,
 * ou si la sous-clé est datée de plus de 5 minutes après `now` : le serveur la refuse à la publication (ADR 0025,
 * point 7), elle n'a rien à faire dans sa réponse.
 */
async function verifiedSubkey(
  served: ServedSubkey,
  userId: string,
  certSerial: Uint8Array,
  ikPublic: Uint8Array,
  now: number,
): Promise<VerifiedSubkey | undefined> {
  let ekId: Uint8Array<ArrayBuffer>
  let ekPublic: Uint8Array<ArrayBuffer>
  let signature: Uint8Array<ArrayBuffer>
  try {
    ekId = fromBase64Url(served.ekId)
    ekPublic = fromBase64Url(served.ekPublic)
    signature = fromBase64Url(served.signature)
  } catch {
    return undefined
  }
  if (
    served.kem !== KEM ||
    ekId.length !== EK_ID_BYTES ||
    ekPublic.length !== EK_PUBLIC_BYTES ||
    !Number.isSafeInteger(served.createdAt) ||
    served.createdAt > now + CLOCK_SKEW_MS ||
    served.expiresAt - served.createdAt !== SUBKEY_VALIDITY_MS
  ) {
    return undefined
  }
  const binding = encodeSubkeyBinding({
    userId,
    certSerial,
    ekId,
    kem: KEM,
    ekPublic,
    createdAt: served.createdAt,
    expiresAt: served.expiresAt,
  })
  if (!(await verifyEd25519(ikPublic, binding, signature))) {
    return undefined
  }
  return { ekId, ekPublic, createdAt: served.createdAt, expiresAt: served.expiresAt }
}
