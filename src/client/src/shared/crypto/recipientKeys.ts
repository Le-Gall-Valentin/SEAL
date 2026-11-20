/**
 * KEY-20, PROTOCOL.md 6.3 (ADR 0030, point 5) : un émetteur utilise toujours la sous-clé non expirée la plus récente
 * du destinataire, après avoir vérifié le certificat qui la porte et sa liaison. Un certificat ou une seule liaison
 * invalides refusent toutes les clés du destinataire : une signature fausse n'est jamais normale, c'est un serveur qui
 * ment.
 */
import { fromBase64Url } from './base64url'
import { uuid } from './canonical'
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

/**
 * Sous-clé retenue pour un destinataire, avec le certificat vérifié qui la porte. Ses champs sont des copies, à
 * afficher : sealMessage n'accepte que l'objet rendu par chooseSubkey, et scelle avec la copie qu'il garde.
 */
export interface ChosenSubkey {
  readonly recipientId: string
  readonly certSerial: Uint8Array<ArrayBuffer>
  readonly ikPublic: Uint8Array<ArrayBuffer>
  readonly ekId: Uint8Array<ArrayBuffer>
  readonly ekPublic: Uint8Array<ArrayBuffer>
}

/** ADR 0030, point 8 : la sous-clé vérifiée, copiée au moment du choix ; la seule que le scellement utilise. */
export interface VerifiedRecipient {
  readonly recipientId: string
  readonly ekId: Uint8Array<ArrayBuffer>
  readonly ekPublic: Uint8Array<ArrayBuffer>
  /** Fin de validité de la sous-clé (KEY-21) : elle ne scelle plus au-delà (KEY-20). */
  readonly expiresAt: number
  /** Fin de validité du certificat qui la porte (KEY-04). */
  readonly certificateNotAfter: number
}

/** Sous-clés rendues par chooseSubkey, hors de leur interface publique (comme les secrets d'un trousseau). */
const CHOSEN = new WeakMap<ChosenSubkey, VerifiedRecipient>()

/** Interne à shared/crypto : la copie vérifiée d'une sous-clé rendue par chooseSubkey ; toute autre lève TypeError. */
export function verifiedRecipient(chosen: ChosenSubkey): VerifiedRecipient {
  const verified = CHOSEN.get(chosen)
  if (verified === undefined) {
    throw new TypeError('KEY-20 : sous-clé non choisie par chooseSubkey')
  }
  return verified
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
  const recipient = lowercaseUuid(recipientId)
  if (!isObject(served)) {
    throw new SubkeyUnavailableError('certificate')
  }
  let certificate
  try {
    certificate = await verifyCertificate(store, served.certificate, recipient, now, CLOCK_SKEW_MS)
  } catch (error) {
    if (error instanceof CertificateRejectedError) {
      throw new SubkeyUnavailableError('certificate', { cause: error })
    }
    throw error
  }
  if (!isList(served.subkeys)) {
    throw new SubkeyUnavailableError('binding')
  }
  const subkeys = await Promise.all(
    served.subkeys.map((subkey) =>
      verifiedSubkey(subkey, recipient, certificate.serial, certificate.ikPublic, now),
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
  const chosen: ChosenSubkey = Object.freeze({
    recipientId: recipient,
    certSerial: Uint8Array.from(certificate.serial),
    ikPublic: Uint8Array.from(certificate.ikPublic),
    ekId: Uint8Array.from(newest.ekId),
    ekPublic: Uint8Array.from(newest.ekPublic),
  })
  CHOSEN.set(chosen, {
    recipientId: recipient,
    ekId: newest.ekId,
    ekPublic: newest.ekPublic,
    expiresAt: newest.expiresAt,
    certificateNotAfter: certificate.notAfter,
  })
  return chosen
}

/** Une réponse hostile peut servir autre chose qu'une liste ; ce test ne change pas le type supposé. */
function isList(value: unknown): boolean {
  return Array.isArray(value)
}

/** De même pour un objet : une réponse nulle n'a pas de certificat. */
function isObject(value: unknown): boolean {
  return typeof value === 'object' && value !== null
}

/** Un identifiant à la forme UUID, comparé en minuscules ; une autre forme est une erreur de l'appelant. */
function lowercaseUuid(value: string): string {
  try {
    uuid(value)
  } catch (error) {
    throw new TypeError('KEY-20 : identifiant de destinataire hors de la forme UUID', {
      cause: error,
    })
  }
  return value.toLowerCase()
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
  let binding: Uint8Array<ArrayBuffer>
  try {
    ekId = fromBase64Url(served.ekId)
    ekPublic = fromBase64Url(served.ekPublic)
    signature = fromBase64Url(served.signature)
    if (
      served.kem !== KEM ||
      ekId.length !== EK_ID_BYTES ||
      ekPublic.length !== EK_PUBLIC_BYTES ||
      !Number.isSafeInteger(served.createdAt) ||
      !Number.isSafeInteger(served.expiresAt) ||
      served.createdAt < 0 ||
      served.createdAt > now + CLOCK_SKEW_MS ||
      served.expiresAt - served.createdAt !== SUBKEY_VALIDITY_MS
    ) {
      return undefined
    }
    binding = encodeSubkeyBinding({
      userId,
      certSerial,
      ekId,
      kem: KEM,
      ekPublic,
      createdAt: served.createdAt,
      expiresAt: served.expiresAt,
    })
  } catch {
    // Réponse hors des types attendus (champ nul, valeur impossible à encoder) : une liaison invalide, rien d'autre.
    return undefined
  }
  if (!(await verifyEd25519(ikPublic, binding, signature))) {
    return undefined
  }
  return { ekId, ekPublic, createdAt: served.createdAt, expiresAt: served.expiresAt }
}
