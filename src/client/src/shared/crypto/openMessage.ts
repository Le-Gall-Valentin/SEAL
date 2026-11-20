/**
 * CDC 7.7, PROTOCOL.md 6.5 (ADR 0030, point 4) : ouverture et vérification d'un message reçu, en échouant fermé.
 */
import { z } from 'zod'
import { fromBase64Url } from './base64url'
import { equalBytes } from './bytes'
import { CanonicalReader } from './canonicalReader'
import { CertificateRejectedError, type ServedCertificate, verifyCertificate } from './certificate'
import { verifyEd25519 } from './ed25519'
import { openCek } from './hpke'
import { type Keyring, keyringSecrets } from './keyring'
import type { KeyStore } from './keystore'
import {
  decodeSignedPayload,
  encodeMessageAad,
  encodeWrapInfo,
  type EnvelopeHeader,
  MAX_RECIPIENTS,
  MESSAGE_SUITE,
  MessageFormatError,
  NONCE_BYTES,
  type SignedPayloadFields,
} from './messageFormat'
import { EK_ID_BYTES, SERIAL_BYTES } from './protocol'

const ENC_BYTES = 32
const WRAPPED_CEK_BYTES = 48
/** Étiquette AES-GCM de 128 bits : un chiffré en compte au moins 16 octets. */
const TAG_BYTES = 16
/** MSG-21 : chiffré de 24 Kio au plus, une fois décodé (ADR 0030, point 7). */
const MAX_CIPHERTEXT_BYTES = 24 * 1024

/** RFC 9562, forme canonique : 8-4-4-4-12 en minuscules (ADR 0030, point 4). */
const LOWERCASE_UUID = z
  .string()
  .regex(/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/)

/** CDC 7.7, étape 1 : schéma de l'enveloppe, sans champ inconnu ; les octets sont relus ensuite par `sized`. */
const EnvelopeSchema = z.strictObject({
  v: z.literal(1),
  suite: z.literal(MESSAGE_SUITE),
  messageId: LOWERCASE_UUID,
  conversationId: LOWERCASE_UUID,
  senderId: LOWERCASE_UUID,
  senderCertSerial: z.string(),
  nonce: z.string(),
  // En base64url, 24 Kio tiennent en 32 768 caractères : rien de plus long n'est décodé.
  ciphertext: z.string().max(Math.ceil(MAX_CIPHERTEXT_BYTES / 3) * 4),
  wraps: z
    .array(
      z.strictObject({
        recipientId: LOWERCASE_UUID,
        ekId: z.string(),
        enc: z.string(),
        wrappedCek: z.string(),
      }),
    )
    .max(MAX_RECIPIENTS),
})

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
  readonly aad: Uint8Array<ArrayBuffer>
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
  envelope: unknown,
  context: IncomingContext,
): Promise<OpenedMessage> {
  try {
    const decoded = decodeEnvelope(envelope)
    const plaintext = await decrypted(keyring, decoded)
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

/**
 * Étape 1 : schéma strict (aucun champ inconnu, UUID en minuscules), base64url canonique, tailles, version, suite et
 * MSG-21 (chiffré de 24 576 octets au plus, 50 encapsulations au plus, une par destinataire) ; tout écart vaut
 * `integrity_failed`. L'AAD est calculée ici : un en-tête impossible à encoder est refusé de même.
 */
function decodeEnvelope(envelope: unknown): DecodedEnvelope {
  try {
    const parsed = EnvelopeSchema.parse(envelope)
    const header: EnvelopeHeader = {
      v: 1,
      suite: MESSAGE_SUITE,
      messageId: parsed.messageId,
      conversationId: parsed.conversationId,
      senderId: parsed.senderId,
      senderCertSerial: sized(parsed.senderCertSerial, SERIAL_BYTES),
    }
    const ciphertext = fromBase64Url(parsed.ciphertext)
    if (ciphertext.length < TAG_BYTES || ciphertext.length > MAX_CIPHERTEXT_BYTES) {
      throw new MessageFormatError('MSG-21 : chiffré de 16 à 24 576 octets')
    }
    if (new Set(parsed.wraps.map((wrap) => wrap.recipientId)).size !== parsed.wraps.length) {
      throw new MessageFormatError('MSG-21 : une encapsulation par destinataire')
    }
    return {
      header,
      aad: encodeMessageAad(header),
      nonce: sized(parsed.nonce, NONCE_BYTES),
      ciphertext,
      wraps: parsed.wraps.map((wrap) => ({
        recipientId: wrap.recipientId,
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
 * Étapes 3 et 4 : l'encapsulation pour soi, dont la sous-clé est dans le trousseau, ouverte par HPKE (sinon
 * `undecryptable`) ; puis `Plaintext` déchiffré par AES-GCM sous l'AAD (sinon `integrity_failed`).
 */
async function decrypted(
  keyring: Keyring,
  envelope: DecodedEnvelope,
): Promise<Uint8Array<ArrayBuffer>> {
  const readerId = keyring.userId.toLowerCase()
  const wrap = envelope.wraps.find((candidate) => candidate.recipientId === readerId)
  const index =
    wrap === undefined
      ? -1
      : keyring.subkeys.findIndex((subkey) => equalBytes(subkey.ekId, wrap.ekId))
  const privateKey = keyringSecrets(keyring).subkeyKeys[index]
  const subkey = keyring.subkeys[index]
  if (wrap === undefined || privateKey === undefined || subkey === undefined) {
    throw new Rejected('undecryptable')
  }
  let cek: Uint8Array<ArrayBuffer>
  try {
    cek = await openCek(
      { privateKey, publicKey: subkey.publicKey },
      wrap.enc,
      wrap.wrappedCek,
      encodeWrapInfo(envelope.header, wrap.recipientId, wrap.ekId),
      envelope.aad,
    )
  } catch {
    // openCek ne lève que HpkeOpenError : autre clé, autre en-tête ou encapsulation altérée.
    throw new Rejected('undecryptable')
  }
  try {
    const key = await crypto.subtle.importKey('raw', cek, 'AES-GCM', false, ['decrypt'])
    return new Uint8Array(
      await crypto.subtle.decrypt(
        { name: 'AES-GCM', iv: envelope.nonce, additionalData: envelope.aad },
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

function sized(text: string, length: number): Uint8Array<ArrayBuffer> {
  const value = fromBase64Url(text)
  if (value.length !== length) {
    throw new MessageFormatError(`enveloppe : ${String(length)} octets attendus`)
  }
  return value
}
