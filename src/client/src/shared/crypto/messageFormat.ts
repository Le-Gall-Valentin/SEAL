/**
 * Format d'un message SEAL v1 (CDC 7.3, PROTOCOL.md 6.2, ADR 0030) : structures signées et authentifiées d'un message,
 * et leur relecture stricte. `SignedPayload` est signé par l'IK de l'émetteur ; l'AAD lie le chiffré à l'en-tête
 * visible de l'enveloppe ; `info_r` lie chaque encapsulation de la CEK à son destinataire et à sa sous-clé.
 */
import {
  bytes,
  CanonicalEncodingError,
  list,
  struct,
  text,
  u64,
  u8,
  uuid as encodeUuid,
} from './canonical'
import { CanonicalReader } from './canonicalReader'
import { SERIAL_BYTES } from './protocol'

export const MESSAGE_SUITE = 'SEAL1-X25519-A256GCM-ED25519'
export type MessageSuite = typeof MESSAGE_SUITE

/** CDC 7.3 : `text/plain` en v1. */
export const CONTENT_TYPE = 'text/plain'
export const SALT_BYTES = 16
export const NONCE_BYTES = 12
/** MSG-21 : 50 encapsulations au plus, une par membre. */
export const MAX_RECIPIENTS = 50
/** CDC 7.3 : 4 000 caractères au plus après NFC, comptés en points de code (ADR 0030, point 6). */
export const MAX_BODY_CODE_POINTS = 4000

/** Le format d'un message n'est pas respecté : il n'est ni écrit, ni lu. */
export class MessageFormatError extends Error {
  override readonly name = 'MessageFormatError'
}

/** En-tête visible de l'enveloppe, repris dans l'AAD et dans `SignedPayload`. */
export interface EnvelopeHeader {
  readonly v: 1
  readonly suite: MessageSuite
  readonly messageId: string
  readonly conversationId: string
  readonly senderId: string
  readonly senderCertSerial: Uint8Array
}

/** CDC 7.3 : champs de `SignedPayload`, dans l'ordre de la structure. Identifiants en minuscules. */
export interface SignedPayloadFields extends EnvelopeHeader {
  /** 1 : direct ; 2 : groupe. */
  readonly conversationType: 1 | 2
  /** Horloge de l'émetteur, en millisecondes. */
  readonly sentAt: number
  readonly senderSeq: number
  readonly groupSeq: number
  readonly salt: Uint8Array
  /** Triés par ordre des octets, sans doublon, émetteur compris. */
  readonly recipientIds: readonly string[]
  readonly contentType: string
  readonly body: string
}

/** Encapsulation de la CEK pour un destinataire, telle que la transporte l'enveloppe (base64url). */
export interface WrapWire {
  readonly recipientId: string
  readonly ekId: string
  readonly enc: string
  readonly wrappedCek: string
}

/** CDC 7.3 : enveloppe transmise sur la WebSocket, seule partie visible du serveur ; octets en base64url. */
export interface EnvelopeWire {
  readonly v: number
  readonly suite: string
  readonly messageId: string
  readonly conversationId: string
  readonly senderId: string
  readonly senderCertSerial: string
  readonly nonce: string
  readonly ciphertext: string
  readonly wraps: readonly WrapWire[]
}

/** `SignedPayload = struct("SEAL-msg-v1", …)` ; un champ hors du format lève MessageFormatError. */
export function encodeSignedPayload(fields: SignedPayloadFields): Uint8Array<ArrayBuffer> {
  checkPayload(fields)
  return encoded(() =>
    struct(
      'SEAL-msg-v1',
      u8(fields.v),
      text(fields.suite),
      encodeUuid(fields.messageId),
      encodeUuid(fields.conversationId),
      u8(fields.conversationType),
      encodeUuid(fields.senderId),
      bytes(fields.senderCertSerial),
      u64(fields.sentAt),
      u64(fields.senderSeq),
      u64(fields.groupSeq),
      bytes(fields.salt),
      list(encodeUuid, fields.recipientIds),
      text(fields.contentType),
      text(fields.body),
    ),
  )
}

/** `AAD = struct("SEAL-aad-v1", u8(v), text(suite), uuid(messageId), uuid(conversationId), uuid(senderId), …)`. */
export function encodeMessageAad(header: EnvelopeHeader): Uint8Array<ArrayBuffer> {
  checkHeader(header)
  return encoded(() =>
    struct(
      'SEAL-aad-v1',
      u8(header.v),
      text(header.suite),
      encodeUuid(header.messageId),
      encodeUuid(header.conversationId),
      encodeUuid(header.senderId),
      bytes(header.senderCertSerial),
    ),
  )
}

/** `info_r = struct("SEAL-wrap-v1", text(suite), uuid(messageId), uuid(conversationId), uuid(recipientId), …)`. */
export function encodeWrapInfo(
  header: EnvelopeHeader,
  recipientId: string,
  ekId: Uint8Array,
): Uint8Array<ArrayBuffer> {
  checkHeader(header)
  return encoded(() =>
    struct(
      'SEAL-wrap-v1',
      text(header.suite),
      encodeUuid(header.messageId),
      encodeUuid(header.conversationId),
      encodeUuid(recipientId),
      bytes(ekId),
    ),
  )
}

/**
 * MSG-01, CDC 7.7 (étape 5) : relecture stricte de `SignedPayload` : étiquette, version, suite, tailles, type de
 * conversation, destinataires triés et distincts, type de contenu, corps en NFC d'au plus 4 000 points de code, et
 * aucun octet après le dernier champ. Tout écart lève MessageFormatError.
 */
export function decodeSignedPayload(signedPayload: Uint8Array): SignedPayloadFields {
  const fields = encoded(() => {
    const reader = CanonicalReader.of(signedPayload, 'SEAL-msg-v1')
    const v = reader.u8()
    const suite = reader.text()
    const messageId = reader.uuid()
    const conversationId = reader.uuid()
    const conversationType = reader.u8()
    const senderId = reader.uuid()
    const senderCertSerial = reader.bytes()
    const sentAt = reader.safeU64()
    const senderSeq = reader.safeU64()
    const groupSeq = reader.safeU64()
    const salt = reader.bytes()
    const recipientIds = reader.list(() => reader.uuid())
    const contentType = reader.text()
    const body = reader.text()
    reader.end()
    if (v !== 1 || suite !== MESSAGE_SUITE || (conversationType !== 1 && conversationType !== 2)) {
      throw new MessageFormatError(
        'SignedPayload : version, suite ou type de conversation inconnus',
      )
    }
    return {
      v,
      suite,
      messageId,
      conversationId,
      senderId,
      senderCertSerial,
      conversationType,
      sentAt,
      senderSeq,
      groupSeq,
      salt,
      recipientIds,
      contentType,
      body,
    } satisfies SignedPayloadFields
  })
  checkPayload(fields)
  return fields
}

/** Version et suite sont fixées par le type ; seule la taille du numéro de série reste à contrôler. */
function checkHeader(header: EnvelopeHeader): void {
  if (header.senderCertSerial.length !== SERIAL_BYTES) {
    throw new MessageFormatError('en-tête : le numéro de série fait 16 octets')
  }
}

function checkPayload(fields: SignedPayloadFields): void {
  checkHeader(fields)
  if (fields.salt.length !== SALT_BYTES) {
    throw new MessageFormatError('SignedPayload : le sel fait 16 octets')
  }
  if (fields.contentType !== CONTENT_TYPE) {
    throw new MessageFormatError('SignedPayload : seul text/plain existe en v1')
  }
  if (Array.from(fields.body.normalize('NFC')).length > MAX_BODY_CODE_POINTS) {
    throw new MessageFormatError('SignedPayload : corps de plus de 4 000 points de code')
  }
  const ids = fields.recipientIds
  if (ids.length === 0 || ids.length > MAX_RECIPIENTS) {
    throw new MessageFormatError('SignedPayload : de 1 à 50 destinataires')
  }
  let previous: string | undefined
  for (const id of ids) {
    if (previous !== undefined && compareUuids(previous, id) >= 0) {
      throw new MessageFormatError('SignedPayload : destinataires non triés ou en double')
    }
    previous = id
  }
  checkInvariants(fields)
}

/**
 * PROTOCOL.md 6.2 (ADR 0030, point 4) : `messageId` est un UUIDv7 daté de `sentAt`, `senderSeq` commence à 1,
 * `groupSeq` vaut 0 en conversation directe, et l'émetteur figure parmi les destinataires.
 */
function checkInvariants(fields: SignedPayloadFields): void {
  if (fields.senderSeq < 1) {
    throw new MessageFormatError('SignedPayload : senderSeq commence à 1')
  }
  if (fields.conversationType === 1 && fields.groupSeq !== 0) {
    throw new MessageFormatError('SignedPayload : groupSeq vaut 0 en conversation directe')
  }
  if (!fields.recipientIds.includes(fields.senderId)) {
    throw new MessageFormatError('SignedPayload : l’émetteur figure parmi les destinataires')
  }
  if (!Number.isSafeInteger(fields.sentAt)) {
    throw new MessageFormatError('SignedPayload : sentAt est un entier')
  }
  const id = encoded(() => encodeUuid(fields.messageId))
  const view = new DataView(id.buffer)
  if (
    view.getUint8(6) >> 4 !== 7 ||
    view.getUint8(8) >> 6 !== 0b10 ||
    view.getBigUint64(0) >> 16n !== BigInt(fields.sentAt)
  ) {
    throw new MessageFormatError('SignedPayload : messageId est un UUIDv7 daté de sentAt')
  }
}

/**
 * Ordre des octets de deux UUID (RFC 9562) : leurs formes 8-4-4-4-12 en minuscules se comparent comme leurs octets,
 * les chiffres hexadécimaux et les tirets étant aux mêmes places.
 */
export function compareUuids(left: string, right: string): number {
  const a = encoded(() => canonicalUuid(left))
  const b = encoded(() => canonicalUuid(right))
  return a < b ? -1 : a > b ? 1 : 0
}

/** Un UUID à la forme 8-4-4-4-12, rendu en minuscules. */
function canonicalUuid(value: string): string {
  encodeUuid(value)
  return value.toLowerCase()
}

/** Une valeur refusée par l'encodage canonique est un message hors du format. */
function encoded<T>(build: () => T): T {
  try {
    return build()
  } catch (error) {
    if (error instanceof CanonicalEncodingError) {
      throw new MessageFormatError(error.message, { cause: error })
    }
    throw error
  }
}
