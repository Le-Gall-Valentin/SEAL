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
import { equalBytes } from './bytes'
import { CertificateRejectedError, type ServedCertificate, verifyCertificate } from './certificate'
import { verifyEd25519 } from './ed25519'
import { openCek, sealCek } from './hpke'
import { type Keyring, keyringSecrets } from './keyring'
import type { KeyStore } from './keystore'
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
const ENC_BYTES = 32
const WRAPPED_CEK_BYTES = 48
/** Étiquette AES-GCM de 128 bits : un chiffré en compte au moins 16 octets. */
const TAG_BYTES = 16
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

/** CDC 7.7 : état d'un message reçu, du point de vue de la cryptographie (ADR 0030, point 4). */
export type MessageRejection =
  'undecryptable' | 'integrity_failed' | 'signature_invalid' | 'recipients_mismatch'

/** Message vérifié : les champs signés, et de quoi accuser réception ou prouver (P1). */
export interface ReceivedMessage extends SignedPayloadFields {
  /** IK du certificat de l'émetteur, pour le registre des contacts (KEY-50, lot 4). */
  readonly senderIkPublic: Uint8Array<ArrayBuffer>
  readonly signedPayload: Uint8Array<ArrayBuffer>
  readonly signature: Uint8Array<ArrayBuffer>
}

export type OpenedMessage =
  | { readonly status: 'verified'; readonly message: ReceivedMessage }
  | { readonly status: MessageRejection }

export interface IncomingContext {
  /** Certificat de l'émetteur, tel que le sert le serveur. */
  readonly senderCertificate: ServedCertificate
  /** `serverReceivedAt` : le `sentAt` de l'émetteur n'est pas fiable (CDC 7.7, étape 6). */
  readonly receivedAt: number
}

/** Un refus d'une étape de l'ouverture, avec son statut. */
class Rejected extends Error {
  readonly status: MessageRejection

  constructor(status: MessageRejection) {
    super(status)
    this.status = status
  }
}

interface DecodedEnvelope {
  readonly header: EnvelopeHeader
  readonly nonce: Uint8Array<ArrayBuffer>
  readonly ciphertext: Uint8Array<ArrayBuffer>
  readonly wraps: readonly {
    readonly recipientId: string
    readonly ekId: Uint8Array<ArrayBuffer>
    readonly enc: Uint8Array<ArrayBuffer>
    readonly wrappedCek: Uint8Array<ArrayBuffer>
  }[]
}

/**
 * CDC 7.7, PROTOCOL.md 6.5 (ADR 0030, point 4) : ouvre une enveloppe reçue et vérifie, dans l'ordre et en échouant
 * fermé, l'enveloppe, sa propre encapsulation, le chiffré, le contenu signé, le certificat de l'émetteur à
 * `receivedAt`, sa signature, puis que le lecteur figure parmi les destinataires. Le contenu n'est rendu qu'au
 * statut `verified` ; un refus ne lève rien.
 */
export async function openMessage(
  store: KeyStore,
  keyring: Keyring,
  envelope: EnvelopeWire,
  context: IncomingContext,
): Promise<OpenedMessage> {
  try {
    const decoded = decodeEnvelope(envelope)
    const aad = encodeMessageAad(decoded.header)
    const plaintext = await decrypted(keyring, decoded, aad)
    let parts
    try {
      parts = plaintextParts(plaintext)
    } finally {
      plaintext.fill(0)
    }
    const fields = signedFields(parts.signedPayload, decoded.header)
    const certificate = await senderCertificate(store, context, decoded.header)
    if (!(await verifyEd25519(certificate.ikPublic, parts.signedPayload, parts.signature))) {
      throw new Rejected('signature_invalid')
    }
    if (!fields.recipientIds.includes(keyring.userId.toLowerCase())) {
      throw new Rejected('recipients_mismatch')
    }
    return {
      status: 'verified',
      message: {
        ...fields,
        senderIkPublic: Uint8Array.from(certificate.ikPublic),
        signedPayload: parts.signedPayload,
        signature: parts.signature,
      },
    }
  } catch (error) {
    if (error instanceof Rejected) {
      return { status: error.status }
    }
    throw error
  }
}

/** Étape 1 : base64url canonique, tailles, version et suite ; tout écart vaut `integrity_failed`. */
function decodeEnvelope(envelope: EnvelopeWire): DecodedEnvelope {
  try {
    if (envelope.v !== 1 || envelope.suite !== MESSAGE_SUITE) {
      throw new MessageFormatError('enveloppe : version ou suite inconnues')
    }
    const header: EnvelopeHeader = {
      v: 1,
      suite: MESSAGE_SUITE,
      messageId: canonicalUuid(envelope.messageId),
      conversationId: canonicalUuid(envelope.conversationId),
      senderId: canonicalUuid(envelope.senderId),
      senderCertSerial: sized(envelope.senderCertSerial, SERIAL_BYTES),
    }
    const ciphertext = fromBase64Url(envelope.ciphertext)
    if (ciphertext.length < TAG_BYTES || envelope.wraps.length > MAX_RECIPIENTS) {
      throw new MessageFormatError('enveloppe : chiffré trop court ou trop d’encapsulations')
    }
    return {
      header,
      nonce: sized(envelope.nonce, NONCE_BYTES),
      ciphertext,
      wraps: envelope.wraps.map((wrap) => ({
        recipientId: canonicalUuid(wrap.recipientId),
        ekId: sized(wrap.ekId, EK_ID_BYTES),
        enc: sized(wrap.enc, ENC_BYTES),
        wrappedCek: sized(wrap.wrappedCek, WRAPPED_CEK_BYTES),
      })),
    }
  } catch {
    throw new Rejected('integrity_failed')
  }
}

/**
 * Étapes 3 et 4 : une encapsulation pour soi, dont la sous-clé est dans le trousseau, ouverte par HPKE (sinon
 * `undecryptable`) ; puis `Plaintext` déchiffré par AES-GCM sous l'AAD (sinon `integrity_failed`).
 */
async function decrypted(
  keyring: Keyring,
  envelope: DecodedEnvelope,
  aad: Uint8Array<ArrayBuffer>,
): Promise<Uint8Array<ArrayBuffer>> {
  const readerId = keyring.userId.toLowerCase()
  const { subkeyKeys } = keyringSecrets(keyring)
  let cek: Uint8Array<ArrayBuffer> | undefined
  for (const wrap of envelope.wraps) {
    const index = keyring.subkeys.findIndex((subkey) => equalBytes(subkey.ekId, wrap.ekId))
    const privateKey = subkeyKeys[index]
    const subkey = keyring.subkeys[index]
    if (wrap.recipientId !== readerId || privateKey === undefined || subkey === undefined) {
      continue
    }
    try {
      cek = await openCek(
        { privateKey, publicKey: subkey.publicKey },
        wrap.enc,
        wrap.wrappedCek,
        encodeWrapInfo(envelope.header, wrap.recipientId, wrap.ekId),
        aad,
      )
      break
    } catch {
      // openCek ne lève que HpkeOpenError : une autre encapsulation pour soi peut encore s'ouvrir.
    }
  }
  if (cek === undefined) {
    throw new Rejected('undecryptable')
  }
  try {
    const key = await crypto.subtle.importKey('raw', cek, 'AES-GCM', false, ['decrypt'])
    return new Uint8Array(
      await crypto.subtle.decrypt(
        { name: 'AES-GCM', iv: envelope.nonce, additionalData: aad },
        key,
        envelope.ciphertext,
      ),
    )
  } catch {
    throw new Rejected('integrity_failed')
  } finally {
    cek.fill(0)
  }
}

/** `Plaintext = bytes(SignedPayload) || bytes(signature) || bytes(padding)` ; le padding est ignoré (point 7). */
function plaintextParts(plaintext: Uint8Array): {
  readonly signedPayload: Uint8Array<ArrayBuffer>
  readonly signature: Uint8Array<ArrayBuffer>
} {
  try {
    const reader = CanonicalReader.unlabelled(plaintext)
    const signedPayload = reader.bytes()
    const signature = reader.bytes()
    reader.bytes()
    reader.end()
    return { signedPayload, signature }
  } catch {
    throw new Rejected('integrity_failed')
  }
}

/** Étape 5 : `SignedPayload` relu strictement, son en-tête égal à celui de l'enveloppe. */
function signedFields(signedPayload: Uint8Array, header: EnvelopeHeader): SignedPayloadFields {
  let fields: SignedPayloadFields
  try {
    fields = decodeSignedPayload(signedPayload)
  } catch {
    throw new Rejected('integrity_failed')
  }
  if (
    fields.messageId !== header.messageId ||
    fields.conversationId !== header.conversationId ||
    fields.senderId !== header.senderId ||
    !equalBytes(fields.senderCertSerial, header.senderCertSerial)
  ) {
    throw new Rejected('integrity_failed')
  }
  return fields
}

/** Étape 6 : certificat de l'émetteur, à son numéro de série, vérifié à `receivedAt` (sans tolérance : heure serveur). */
async function senderCertificate(
  store: KeyStore,
  context: IncomingContext,
  header: EnvelopeHeader,
): Promise<{ readonly ikPublic: Uint8Array }> {
  try {
    const certificate = await verifyCertificate(
      store,
      context.senderCertificate,
      header.senderId,
      context.receivedAt,
      0,
    )
    if (!equalBytes(certificate.serial, header.senderCertSerial)) {
      throw new Rejected('signature_invalid')
    }
    return certificate
  } catch (error) {
    if (error instanceof CertificateRejectedError) {
      throw new Rejected('signature_invalid')
    }
    throw error
  }
}

function canonicalUuid(value: string): string {
  encodeUuid(value)
  return value.toLowerCase()
}

function sized(text: string, length: number): Uint8Array<ArrayBuffer> {
  const value = fromBase64Url(text)
  if (value.length !== length) {
    throw new MessageFormatError(`enveloppe : ${String(length)} octets attendus`)
  }
  return value
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
  const view = new DataView(value.buffer)
  view.setBigUint64(0, BigInt(millis) << 16n)
  value.set(random, 6)
  view.setUint8(6, 0x70 | (view.getUint8(6) & 0x0f))
  view.setUint8(8, 0x80 | (view.getUint8(8) & 0x3f))
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
  let previous: string | undefined
  for (const id of ids) {
    if (previous !== undefined && compareUuids(previous, id) >= 0) {
      throw new MessageFormatError('SignedPayload : destinataires non triés ou en double')
    }
    previous = id
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
