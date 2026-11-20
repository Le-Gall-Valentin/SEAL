/**
 * Messages SEAL v1 (CDC 7.3, PROTOCOL.md section 6, ADR 0030) : structures signées et authentifiées d'un message, et
 * leur relecture stricte. `SignedPayload` est signé par l'IK de l'émetteur ; l'AAD lie le chiffré à l'en-tête visible
 * de l'enveloppe ; `info_r` lie chaque encapsulation de la CEK à son destinataire et à sa sous-clé.
 */
import { toBase64Url, fromBase64Url } from './base64url'
import {
  bytes,
  CanonicalEncodingError,
  concat,
  list,
  struct,
  text,
  u64,
  u8,
  uuid as encodeUuid,
} from './canonical'
import { CanonicalReader } from './canonicalReader'
import { sealCek } from './hpke'
import { type Keyring, keyringSecrets } from './keyring'
import type { ChosenSubkey } from './recipientKeys'

export const MESSAGE_SUITE = 'SEAL1-X25519-A256GCM-ED25519'
export type MessageSuite = typeof MESSAGE_SUITE

/** CDC 7.3 : `text/plain` en v1. */
const CONTENT_TYPE = 'text/plain'
const SERIAL_BYTES = 16
const SALT_BYTES = 16
/** MSG-21 : 50 encapsulations au plus, une par membre. */
const MAX_RECIPIENTS = 50
/** CDC 7.3 : 4 000 caractères au plus après NFC, comptés en points de code (ADR 0030, point 6). */
export const MAX_BODY_CODE_POINTS = 4000

const CEK_BYTES = 32
const NONCE_BYTES = 12
const EK_ID_BYTES = 16
/** RFC 9562 : 74 bits aléatoires d'un UUIDv7, pris dans 10 octets. */
const UUID_RANDOM_BYTES = 10

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

/** CDC 7.3 : le corps dépasse 4 000 points de code après NFC (ADR 0030, point 6) ; le message n'est pas scellé. */
export class MessageTooLongError extends Error {
  override readonly name = 'MessageTooLongError'

  constructor() {
    super(`corps de plus de ${String(MAX_BODY_CODE_POINTS)} points de code`)
  }
}

/** Ce que l'émetteur veut envoyer ; le scellement tire `messageId`, le sel, la CEK et le nonce. */
export interface MessageDraft {
  readonly conversationId: string
  readonly conversationType: 1 | 2
  /** Numéro de série du certificat courant de l'émetteur, en base64url. */
  readonly senderCertSerial: string
  readonly sentAt: number
  readonly senderSeq: number
  readonly groupSeq: number
  readonly body: string
  /** Sous-clé choisie pour chaque destinataire (KEY-20), émetteur compris ; dans un ordre quelconque. */
  readonly recipients: readonly ChosenSubkey[]
}

/** Aléa d'un scellement ; fixé par les vecteurs seulement (interne à shared/crypto, jamais exporté de l'index). */
export interface SealRandomness {
  readonly messageIdRandom: Uint8Array
  readonly salt: Uint8Array
  /** Remise à zéro dès qu'elle a servi (KEY-40). */
  readonly cek: Uint8Array<ArrayBuffer>
  readonly nonce: Uint8Array<ArrayBuffer>
  /** Matériau de la clé éphémère HPKE de chaque destinataire (`ikmE`) ; tiré par HPKE si absent. */
  readonly ephemeral: (recipientId: string) => Uint8Array | undefined
}

/**
 * CDC 7.5, étapes 3 à 7 (PROTOCOL.md 6.4) : signe `SignedPayload` par l'IK du trousseau, chiffre `Plaintext` sous
 * l'AAD avec une CEK et un nonce neufs, puis encapsule la CEK pour chaque destinataire. La CEK et `Plaintext` sont
 * remis à zéro.
 */
export async function sealMessage(keyring: Keyring, draft: MessageDraft): Promise<EnvelopeWire> {
  return sealMessageWith(keyring, draft, {
    messageIdRandom: crypto.getRandomValues(new Uint8Array(UUID_RANDOM_BYTES)),
    salt: crypto.getRandomValues(new Uint8Array(SALT_BYTES)),
    cek: crypto.getRandomValues(new Uint8Array(CEK_BYTES)),
    nonce: crypto.getRandomValues(new Uint8Array(NONCE_BYTES)),
    ephemeral: () => undefined,
  })
}

/** sealMessage, l'aléa donné : réservé aux vecteurs (interne à shared/crypto). */
export async function sealMessageWith(
  keyring: Keyring,
  draft: MessageDraft,
  randomness: SealRandomness,
): Promise<EnvelopeWire> {
  const { cek } = randomness
  let plaintext: Uint8Array<ArrayBuffer> | undefined
  try {
    const body = draft.body.normalize('NFC')
    if (Array.from(body).length > MAX_BODY_CODE_POINTS) {
      throw new MessageTooLongError()
    }
    const recipients = sortedRecipients(keyring.userId, draft.recipients)
    const header: EnvelopeHeader = {
      v: 1,
      suite: MESSAGE_SUITE,
      messageId: uuidV7(draft.sentAt, randomness.messageIdRandom),
      conversationId: draft.conversationId.toLowerCase(),
      senderId: keyring.userId,
      senderCertSerial: fromBase64Url(draft.senderCertSerial),
    }
    const signedPayload = encodeSignedPayload({
      ...header,
      conversationType: draft.conversationType,
      sentAt: draft.sentAt,
      senderSeq: draft.senderSeq,
      groupSeq: draft.groupSeq,
      salt: randomness.salt,
      recipientIds: recipients.map((recipient) => recipient.recipientId),
      contentType: CONTENT_TYPE,
      body,
    })
    const signature = new Uint8Array(
      await crypto.subtle.sign('Ed25519', keyringSecrets(keyring).identityKey, signedPayload),
    )
    // P0 : padding vide (ADR 0030, point 7).
    plaintext = concat(bytes(signedPayload), bytes(signature), bytes(new Uint8Array()))
    const aad = encodeMessageAad(header)
    const key = await crypto.subtle.importKey('raw', cek, 'AES-GCM', false, ['encrypt'])
    const ciphertext = new Uint8Array(
      await crypto.subtle.encrypt(
        { name: 'AES-GCM', iv: randomness.nonce, additionalData: aad },
        key,
        plaintext,
      ),
    )
    const wraps: WrapWire[] = []
    for (const recipient of recipients) {
      const { enc, wrappedCek } = await sealCek(
        recipient.ekPublic,
        encodeWrapInfo(header, recipient.recipientId, recipient.ekId),
        aad,
        cek,
        randomness.ephemeral(recipient.recipientId),
      )
      wraps.push({
        recipientId: recipient.recipientId,
        ekId: toBase64Url(recipient.ekId),
        enc: toBase64Url(enc),
        wrappedCek: toBase64Url(wrappedCek),
      })
    }
    return {
      v: header.v,
      suite: header.suite,
      messageId: header.messageId,
      conversationId: header.conversationId,
      senderId: header.senderId,
      senderCertSerial: toBase64Url(header.senderCertSerial),
      nonce: toBase64Url(randomness.nonce),
      ciphertext: toBase64Url(ciphertext),
      wraps,
    }
  } finally {
    // KEY-40 : la CEK et le texte clair n'existent qu'en mémoire, le temps de sceller.
    cek.fill(0)
    plaintext?.fill(0)
  }
}

/** CDC 7.4 : destinataires distincts, émetteur compris, 50 au plus, triés par ordre des octets. */
function sortedRecipients(
  senderId: string,
  recipients: readonly ChosenSubkey[],
): readonly ChosenSubkey[] {
  const normalized = recipients.map((recipient) => ({
    ...recipient,
    recipientId: recipient.recipientId.toLowerCase(),
  }))
  const ids = new Set(normalized.map((recipient) => recipient.recipientId))
  if (ids.size !== normalized.length) {
    throw new TypeError('CDC 7.4 : un destinataire en double')
  }
  if (!ids.has(senderId.toLowerCase())) {
    throw new TypeError('CDC 7.4 : l’émetteur figure parmi les destinataires')
  }
  if (normalized.length > MAX_RECIPIENTS) {
    throw new TypeError('MSG-21 : 50 destinataires au plus')
  }
  if (normalized.some((recipient) => recipient.ekId.length !== EK_ID_BYTES)) {
    throw new TypeError('KEY-05 : un ekId fait 16 octets')
  }
  return normalized.toSorted((left, right) => compareUuids(left.recipientId, right.recipientId))
}

/** RFC 9562 : 48 bits de millisecondes, version 7, variante 10, puis 74 bits pris dans `random`. */
function uuidV7(millis: number, random: Uint8Array): string {
  if (!Number.isSafeInteger(millis) || millis < 0 || millis >= 2 ** 48) {
    throw new MessageFormatError('UUIDv7 : horodatage hors de 48 bits')
  }
  if (random.length !== UUID_RANDOM_BYTES) {
    throw new RangeError('UUIDv7 : 10 octets aléatoires')
  }
  const value = new Uint8Array(16)
  new DataView(value.buffer).setBigUint64(0, BigInt(millis) << 16n)
  value.set(random, 6)
  value[6] = 0x70 | ((value[6] ?? 0) & 0x0f)
  value[8] = 0x80 | ((value[8] ?? 0) & 0x3f)
  const hex = Array.from(value, (byte) => byte.toString(16).padStart(2, '0')).join('')
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
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
  for (let index = 1; index < ids.length; index += 1) {
    if (compareUuids(ids[index - 1] ?? '', ids[index] ?? '') >= 0) {
      throw new MessageFormatError('SignedPayload : destinataires non triés ou en double')
    }
  }
}

/** Ordre des octets de deux UUID (RFC 9562). */
export function compareUuids(left: string, right: string): number {
  const a = encoded(() => encodeUuid(left))
  const b = encoded(() => encodeUuid(right))
  for (let index = 0; index < a.length; index += 1) {
    const difference = (a[index] ?? 0) - (b[index] ?? 0)
    if (difference !== 0) {
      return difference
    }
  }
  return 0
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
