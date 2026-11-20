/**
 * QA-02 : rejeu de vectors/envelope.json (PROTOCOL.md, section 6 ; ADR 0030), commun aux tests sous Node et dans
 * Chromium. Réservé aux tests.
 */
import { describe, expect, it } from 'vitest'
import { bytes, concat, list, struct, text, u64, u8, uuid } from '../canonical'
import { openCek, sealCek } from '../hpke'
import {
  decodeSignedPayload,
  type EnvelopeHeader,
  type EnvelopeWire,
  encodeMessageAad,
  encodeSignedPayload,
  encodeWrapInfo,
  MESSAGE_SUITE,
  MessageFormatError,
  type SignedPayloadFields,
} from '../messageFormat'
import { base64url, decimal, type Json, number, record, records, string } from './vectorReader'

/** Un UUID de version 4 : jamais un messageId (PROTOCOL.md 6.2). */
const UUID_V4 = '6f1c2a3b-4d5e-4f60-8a7b-9c0d1e2f3a4b'

/** Le même UUIDv7, daté d'une milliseconde plus tôt. */
function otherInstant(messageId: string): string {
  const hex = messageId.replaceAll('-', '')
  const earlier = (Number.parseInt(hex.slice(0, 12), 16) - 1).toString(16).padStart(12, '0')
  const value = `${earlier}${hex.slice(12)}`
  return `${value.slice(0, 8)}-${value.slice(8, 12)}-${value.slice(12, 16)}-${value.slice(16, 20)}-${value.slice(20)}`
}

/** RFC 8410 : en-tête PKCS#8 d'une clé privée X25519 brute de 32 octets. */
const X25519_PKCS8_PREFIX = Uint8Array.of(
  0x30,
  0x2e,
  0x02,
  0x01,
  0x00,
  0x30,
  0x05,
  0x06,
  0x03,
  0x2b,
  0x65,
  0x6e,
  0x04,
  0x22,
  0x04,
  0x20,
)

/** Clé privée X25519 d'un vecteur (RFC 7748), importée non extractable. */
export async function vectorX25519Private(raw: Uint8Array): Promise<CryptoKey> {
  return crypto.subtle.importKey('pkcs8', concat(X25519_PKCS8_PREFIX, raw), 'X25519', false, [
    'deriveBits',
  ])
}

/** L'enveloppe d'un vecteur, lue champ par champ (sans transtypage). */
export function vectorEnvelope(json: Json): EnvelopeWire {
  return {
    v: number(json, 'v'),
    suite: string(json, 'suite'),
    messageId: string(json, 'messageId'),
    conversationId: string(json, 'conversationId'),
    senderId: string(json, 'senderId'),
    senderCertSerial: string(json, 'senderCertSerial'),
    nonce: string(json, 'nonce'),
    ciphertext: string(json, 'ciphertext'),
    wraps: records(json['wraps']).map((wrap) => ({
      recipientId: string(wrap, 'recipientId'),
      ekId: string(wrap, 'ekId'),
      enc: string(wrap, 'enc'),
      wrappedCek: string(wrap, 'wrappedCek'),
    })),
  }
}

export function vectorFields(json: Json): SignedPayloadFields {
  const conversationType = number(json, 'conversationType')
  if (conversationType !== 1 && conversationType !== 2) throw new Error('conversationType')
  return {
    v: 1,
    suite: MESSAGE_SUITE,
    messageId: string(json, 'messageId'),
    conversationId: string(json, 'conversationId'),
    senderId: string(json, 'senderId'),
    senderCertSerial: base64url(json, 'senderCertSerial'),
    conversationType,
    sentAt: decimal(json, 'sentAt'),
    senderSeq: decimal(json, 'senderSeq'),
    groupSeq: decimal(json, 'groupSeq'),
    salt: base64url(json, 'salt'),
    recipientIds: (json['recipientIds'] as unknown[]).map(String),
    contentType: string(json, 'contentType'),
    body: string(json, 'body'),
  }
}

export function describeEnvelopeVectors(vectors: Json, engine: string): void {
  const message = record(vectors['message'])
  const fields = vectorFields(record(message['fields']))
  const header: EnvelopeHeader = fields
  const wraps = records(message['wraps'])
  const recipients = records(vectors['recipients'])

  describe(`QA-02 : enveloppe d'un message, vecteurs de envelope.json (${engine})`, () => {
    it('QA-02 : SignedPayload, l’AAD et chaque info_r sont encodés à l’octet près', () => {
      expect(encodeSignedPayload(fields)).toEqual(base64url(message, 'signedPayload'))
      expect(encodeMessageAad(header)).toEqual(base64url(message, 'aad'))
      for (const wrap of wraps) {
        expect(
          encodeWrapInfo(header, string(wrap, 'recipientId'), base64url(wrap, 'ekId')),
        ).toEqual(base64url(wrap, 'info'))
      }
    })

    it('MSG-01 : SignedPayload se relit champ par champ, et Plaintext le porte avec sa signature', () => {
      const signedPayload = base64url(message, 'signedPayload')

      expect(decodeSignedPayload(signedPayload)).toEqual(fields)
      expect(
        concat(
          bytes(signedPayload),
          bytes(base64url(message, 'signature')),
          bytes(new Uint8Array()),
        ),
      ).toEqual(base64url(message, 'plaintext'))
    })

    it('ADR 0030, point 2 : @hpke/core ouvre les encapsulations du générateur, et les reproduit avec sa clé éphémère', async () => {
      const aad = base64url(message, 'aad')
      const cek = base64url(message, 'cek')
      for (const wrap of wraps) {
        const recipient = recipients.find(
          (candidate) => string(candidate, 'userId') === string(wrap, 'recipientId'),
        )
        if (recipient === undefined) throw new Error('destinataire du vecteur introuvable')
        const key = {
          privateKey: await vectorX25519Private(base64url(recipient, 'ekPrivate')),
          publicKey: base64url(recipient, 'ekPublic'),
        }
        const info = base64url(wrap, 'info')

        expect(
          await openCek(key, base64url(wrap, 'enc'), base64url(wrap, 'wrappedCek'), info, aad),
        ).toEqual(cek)
        expect(await sealCek(key.publicKey, info, aad, cek, base64url(wrap, 'ikmE'))).toEqual({
          enc: base64url(wrap, 'enc'),
          wrappedCek: base64url(wrap, 'wrappedCek'),
        })
      }
    })
  })

  describe(`MSG-01 : relecture stricte de SignedPayload (${engine})`, () => {
    /** SignedPayload des vecteurs, un champ remplacé par des octets encodés à la main. */
    function payload(changes: Partial<Record<keyof SignedPayloadFields, Uint8Array>>): Uint8Array {
      const field = (name: keyof SignedPayloadFields, encoded: () => Uint8Array) =>
        changes[name] ?? encoded()
      return struct(
        'SEAL-msg-v1',
        field('v', () => u8(1)),
        field('suite', () => text(MESSAGE_SUITE)),
        field('messageId', () => uuid(fields.messageId)),
        field('conversationId', () => uuid(fields.conversationId)),
        field('conversationType', () => u8(fields.conversationType)),
        field('senderId', () => uuid(fields.senderId)),
        field('senderCertSerial', () => bytes(fields.senderCertSerial)),
        field('sentAt', () => u64(fields.sentAt)),
        field('senderSeq', () => u64(fields.senderSeq)),
        field('groupSeq', () => u64(fields.groupSeq)),
        field('salt', () => bytes(fields.salt)),
        field('recipientIds', () => list(uuid, fields.recipientIds)),
        field('contentType', () => text(fields.contentType)),
        field('body', () => text(fields.body)),
      )
    }

    it('MSG-01 : le SignedPayload reconstruit à la main est celui des vecteurs', () => {
      expect(payload({})).toEqual(base64url(message, 'signedPayload'))
    })

    it('CDC 7.3 : version, suite, type de conversation, tailles, destinataires et corps hors du format sont refusés', () => {
      const [first = '', second = ''] = fields.recipientIds
      for (const [name, forged] of [
        ['version 2', payload({ v: u8(2) })],
        ['autre suite', payload({ suite: text('SEAL1-XWING-A256GCM-ED25519') })],
        ['conversation de type 3', payload({ conversationType: u8(3) })],
        ['numéro de série de 15 octets', payload({ senderCertSerial: bytes(new Uint8Array(15)) })],
        ['sel de 17 octets', payload({ salt: bytes(new Uint8Array(17)) })],
        ['destinataires non triés', payload({ recipientIds: list(uuid, [second, first]) })],
        ['destinataire en double', payload({ recipientIds: list(uuid, [first, first]) })],
        ['aucun destinataire', payload({ recipientIds: list(uuid, []) })],
        ['autre type de contenu', payload({ contentType: text('text/html') })],
        ['corps de 4 001 points de code', payload({ body: text('🦭'.repeat(4001)) })],
        ['octet en trop', concat(payload({}), Uint8Array.of(0))],
      ] as const) {
        expect(() => decodeSignedPayload(forged), name).toThrow(MessageFormatError)
      }
    })

    it('PROTOCOL.md 6.2 : un messageId qui n’est pas un UUIDv7 daté de sentAt, un senderSeq nul, un groupSeq non nul en direct ou l’émetteur absent des destinataires sont refusés', () => {
      const bobId = fields.recipientIds.find((id) => id !== fields.senderId) ?? ''
      for (const [name, forged] of [
        ['UUIDv4', payload({ messageId: uuid(UUID_V4) })],
        [
          'UUIDv7 daté d’un autre instant',
          payload({ messageId: uuid(otherInstant(fields.messageId)) }),
        ],
        ['senderSeq nul', payload({ senderSeq: u64(0) })],
        ['groupSeq non nul en direct', payload({ groupSeq: u64(1) })],
        ['émetteur absent', payload({ recipientIds: list(uuid, [bobId]) })],
      ] as const) {
        expect(() => decodeSignedPayload(forged), name).toThrow(MessageFormatError)
      }
      const group = decodeSignedPayload(payload({ conversationType: u8(2), groupSeq: u64(7) }))
      expect(group.groupSeq).toBe(7)
    })

    it('ADR 0030, point 6 : 4 000 points de code se relisent, émojis compris', () => {
      const body = '🦭'.repeat(4000)

      expect(decodeSignedPayload(payload({ body: text(body) })).body).toBe(body)
    })

    it('CDC 7.3 : l’encodeur refuse ce que la relecture refuserait', () => {
      const [first = '', second = ''] = fields.recipientIds
      for (const [name, forged] of [
        ['destinataires non triés', { ...fields, recipientIds: [second, first] }],
        ['destinataire en double', { ...fields, recipientIds: [first, first] }],
        ['sel de 15 octets', { ...fields, salt: new Uint8Array(15) }],
        ['corps de 4 001 points de code', { ...fields, body: 'a'.repeat(4001) }],
        ['UUIDv4', { ...fields, messageId: UUID_V4 }],
        ['senderSeq nul', { ...fields, senderSeq: 0 }],
        ['groupSeq non nul en direct', { ...fields, groupSeq: 1 }],
        ['sentAt fractionnaire', { ...fields, sentAt: fields.sentAt + 0.5 }],
        [
          'émetteur absent',
          { ...fields, recipientIds: fields.recipientIds.filter((id) => id !== fields.senderId) },
        ],
      ] as const) {
        expect(() => encodeSignedPayload(forged), name).toThrow(MessageFormatError)
      }
    })
  })
}
