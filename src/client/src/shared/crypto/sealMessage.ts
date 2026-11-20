/**
 * CDC 7.5, étapes 3 à 7 (PROTOCOL.md 6.4) : scellement d'un message pour ses destinataires (ADR 0030).
 */
import { toBase64Url, fromBase64Url } from './base64url'
import { bytes, concat, uuidText } from './canonical'
import { sealCek } from './hpke'
import { type Keyring, signWithIdentity } from './keyring'
import {
  compareUuids,
  CONTENT_TYPE,
  encodeMessageAad,
  encodeSignedPayload,
  encodeWrapInfo,
  type EnvelopeHeader,
  type EnvelopeWire,
  MAX_BODY_CODE_POINTS,
  MAX_RECIPIENTS,
  MESSAGE_SUITE,
  MessageFormatError,
  NONCE_BYTES,
  SALT_BYTES,
  type WrapWire,
} from './messageFormat'
import { CEK_BYTES } from './protocol'
import { type ChosenSubkey, verifiedRecipient, type VerifiedRecipient } from './recipientKeys'

/** RFC 9562 : 74 bits aléatoires d'un UUIDv7, pris dans 10 octets. */
const UUID_RANDOM_BYTES = 10

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
  /** Sous-clé rendue par chooseSubkey pour chaque destinataire (KEY-20), émetteur compris ; dans un ordre quelconque. */
  readonly recipients: readonly ChosenSubkey[]
}

/** Aléa d'un scellement ; fixé par les vecteurs seulement (interne à shared/crypto, jamais exporté de l'index). */
export interface SealRandomness {
  readonly messageIdRandom: Uint8Array
  readonly salt: Uint8Array
  /** Remise à zéro dès qu'elle a servi (CDC 7.5, étape 8). */
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
  let signedPayload: Uint8Array<ArrayBuffer> | undefined
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
      senderId: keyring.userId.toLowerCase(),
      senderCertSerial: fromBase64Url(draft.senderCertSerial),
    }
    signedPayload = encodeSignedPayload({
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
    const signature = await signWithIdentity(keyring, signedPayload)
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
    // CDC 7.5, étape 8, et PROTOCOL.md 6.2 : la CEK et le texte clair n'existent qu'en mémoire, le temps de sceller.
    cek.fill(0)
    signedPayload?.fill(0)
    plaintext?.fill(0)
  }
}

/**
 * CDC 7.4 : 50 destinataires au plus, distincts, émetteur compris, triés par ordre des octets. Chacun est la copie
 * vérifiée d'une sous-clé rendue par chooseSubkey (KEY-20, ADR 0030, point 8), identifiant en minuscules.
 */
function sortedRecipients(
  senderId: string,
  recipients: readonly ChosenSubkey[],
): readonly VerifiedRecipient[] {
  if (recipients.length > MAX_RECIPIENTS) {
    throw new TypeError('MSG-21 : 50 destinataires au plus')
  }
  const verified = recipients.map((recipient) => verifiedRecipient(recipient))
  const ids = new Set(verified.map((recipient) => recipient.recipientId))
  if (ids.size !== verified.length) {
    throw new TypeError('CDC 7.4 : un destinataire en double')
  }
  if (!ids.has(senderId.toLowerCase())) {
    throw new TypeError('CDC 7.4 : l’émetteur figure parmi les destinataires')
  }
  return verified.toSorted((left, right) => compareUuids(left.recipientId, right.recipientId))
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
  return uuidText(value)
}
