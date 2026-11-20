/**
 * CDC 7.5 (étapes 3 à 7), PROTOCOL.md 6.4 : scellement d'un message, commun aux tests sous Node et dans Chromium.
 * L'émetteur est celui des vecteurs : trousseau du coffre de vault.json, certificat de identity.json. Réservé aux tests.
 */
import { describe, expect, it } from 'vitest'
import { fromBase64Url } from '../base64url'
import { openCek } from '../hpke'
import { type Keyring, openVault } from '../keyring'
import {
  decodeSignedPayload,
  encodeMessageAad,
  encodeWrapInfo,
  type EnvelopeHeader,
  type EnvelopeWire,
  MESSAGE_SUITE,
  MessageFormatError,
  type SignedPayloadFields,
} from '../messageFormat'
import {
  type MessageDraft,
  MessageTooLongError,
  sealMessage,
  sealMessageWith,
  type SealRandomness,
} from '../sealMessage'
import type { ChosenSubkey } from '../recipientKeys'
import { vectorX25519Private } from './envelopeVectorSuite'
import { vaultPayload, vectorRecoveryKey } from './vaultVectorSuite'
import { base64url, decimal, type Json, number, record, records, string } from './vectorReader'

/** En-tête d'une enveloppe scellée, relu pour recalculer son AAD et ses info_r. */
function headerOf(sealed: EnvelopeWire): EnvelopeHeader {
  return {
    v: 1,
    suite: MESSAGE_SUITE,
    messageId: sealed.messageId,
    conversationId: sealed.conversationId,
    senderId: sealed.senderId,
    senderCertSerial: fromBase64Url(sealed.senderCertSerial),
  }
}

export function describeMessageSeal(envelope: Json, vault: Json, engine: string): void {
  const message = record(envelope['message'])
  const fields = record(message['fields'])
  const sender = record(envelope['sender'])
  const certificate = record(sender['certificate'])
  const wraps = records(message['wraps'])
  const recipients = records(envelope['recipients'])
  const senderId = string(sender, 'userId')
  const bob = recipients.find((recipient) => string(recipient, 'userId') !== senderId) ?? {}

  const chosen: ChosenSubkey[] = recipients.map((recipient) => ({
    recipientId: string(recipient, 'userId'),
    certSerial: base64url(certificate, 'serial'),
    ikPublic: base64url(certificate, 'ikPublic'),
    ekId: base64url(recipient, 'ekId'),
    ekPublic: base64url(recipient, 'ekPublic'),
  }))
  const own = chosen.find((subkey) => subkey.recipientId === senderId)
  const bobs = chosen.find((subkey) => subkey.recipientId !== senderId)
  if (own === undefined || bobs === undefined) throw new Error('l’émetteur et Bob attendus')

  const draft: MessageDraft = {
    conversationId: string(fields, 'conversationId'),
    conversationType: number(fields, 'conversationType') === 2 ? 2 : 1,
    senderCertSerial: string(fields, 'senderCertSerial'),
    sentAt: decimal(fields, 'sentAt'),
    senderSeq: decimal(fields, 'senderSeq'),
    groupSeq: decimal(fields, 'groupSeq'),
    body: string(fields, 'body'),
    // L'ordre donné n'importe pas : le scellement trie les destinataires.
    recipients: [own, bobs],
  }

  /** L'aléa des vecteurs, en copies neuves : le scellement remet sa CEK à zéro. */
  function vectorRandomness(): SealRandomness {
    const messageId = string(fields, 'messageId').replaceAll('-', '')
    return {
      messageIdRandom: Uint8Array.from({ length: 10 }, (_, index) =>
        Number.parseInt(messageId.slice(12 + 2 * index, 14 + 2 * index), 16),
      ),
      salt: base64url(fields, 'salt'),
      cek: base64url(message, 'cek'),
      nonce: base64url(message, 'nonce'),
      ephemeral: (recipientId: string) => {
        const wrap = wraps.find((candidate) => string(candidate, 'recipientId') === recipientId)
        return wrap === undefined ? undefined : base64url(wrap, 'ikmE')
      },
    }
  }

  async function senderKeyring(): Promise<Keyring> {
    return openVault(senderId, vaultPayload(vault['payload']), vectorRecoveryKey(vault))
  }

  /** La CEK et le SignedPayload d'une enveloppe, ouverts avec la clé privée de Bob, comme par un destinataire. */
  async function readAsBob(
    sealed: EnvelopeWire,
  ): Promise<{ readonly cek: Uint8Array; readonly payload: SignedPayloadFields }> {
    const wrap = sealed.wraps.find((candidate) => candidate.recipientId === string(bob, 'userId'))
    if (wrap === undefined) throw new Error('encapsulation de Bob attendue')
    const header = headerOf(sealed)
    const aad = encodeMessageAad(header)
    const cek = await openCek(
      {
        privateKey: await vectorX25519Private(base64url(bob, 'ekPrivate')),
        publicKey: base64url(bob, 'ekPublic'),
      },
      fromBase64Url(wrap.enc),
      fromBase64Url(wrap.wrappedCek),
      encodeWrapInfo(header, wrap.recipientId, fromBase64Url(wrap.ekId)),
      aad,
    )
    const key = await crypto.subtle.importKey('raw', cek, 'AES-GCM', false, ['decrypt'])
    const plaintext = new Uint8Array(
      await crypto.subtle.decrypt(
        { name: 'AES-GCM', iv: fromBase64Url(sealed.nonce), additionalData: aad },
        key,
        fromBase64Url(sealed.ciphertext),
      ),
    )
    const length = new DataView(plaintext.buffer).getUint32(0)
    return { cek, payload: decodeSignedPayload(plaintext.subarray(4, 4 + length)) }
  }

  describe(`CDC 7.5 : scellement d'un message (${engine})`, () => {
    it('QA-02 : l’aléa des vecteurs reproduit l’enveloppe à l’octet près, encapsulations comprises', async () => {
      expect(await sealMessageWith(await senderKeyring(), draft, vectorRandomness())).toEqual(
        record(envelope['envelope']),
      )
    })

    it('KEY-40 : la CEK est remise à zéro une fois encapsulée', async () => {
      const randomness = vectorRandomness()

      await sealMessageWith(await senderKeyring(), draft, randomness)

      expect(randomness.cek.every((byte) => byte === 0)).toBe(true)
    })

    it('CDC 7.5 : messageId est un UUIDv7 à sentAt ; deux scellements tirent un sel, une CEK et un nonce neufs', async () => {
      const keyring = await senderKeyring()
      const [one, two] = [await sealMessage(keyring, draft), await sealMessage(keyring, draft)]
      const hex = one.messageId.replaceAll('-', '')

      expect(Number.parseInt(hex.slice(0, 12), 16)).toBe(draft.sentAt)
      expect(hex[12]).toBe('7')
      expect(['8', '9', 'a', 'b']).toContain(hex[16])
      expect(two.messageId).not.toBe(one.messageId)
      expect(two.nonce).not.toBe(one.nonce)
      const [readOne, readTwo] = [await readAsBob(one), await readAsBob(two)]
      expect(readTwo.cek).not.toEqual(readOne.cek)
      expect(readTwo.payload.salt).not.toEqual(readOne.payload.salt)
      expect(readOne.payload.recipientIds).toEqual([bobs.recipientId, own.recipientId])
    })

    it('ADR 0030, point 6 : 4 000 points de code, émojis compris, passent ; 4 001 sont refusés ; le corps est mis en NFC', async () => {
      const keyring = await senderKeyring()

      const sealed = await sealMessage(keyring, { ...draft, body: `${'🦭'.repeat(3999)}é` })
      expect((await readAsBob(sealed)).payload.body).toBe(`${'🦭'.repeat(3999)}é`)
      await expect(
        sealMessage(keyring, { ...draft, body: '🦭'.repeat(4001) }),
      ).rejects.toBeInstanceOf(MessageTooLongError)
    })

    it('CDC 7.4 : un destinataire en double, l’émetteur absent ou 51 destinataires sont refusés', async () => {
      const keyring = await senderKeyring()
      const many = Array.from({ length: 50 }, (_, index) => ({
        ...bobs,
        recipientId: `0192f7a1-ffff-7000-8000-${index.toString(16).padStart(12, '0')}`,
      }))

      for (const [name, list] of [
        ['destinataire en double', [own, bobs, bobs]],
        ['émetteur absent', [bobs]],
        ['51 destinataires', [own, ...many]],
        ['ekId de 15 octets', [own, { ...bobs, ekId: bobs.ekId.slice(0, 15) }]],
      ] as const) {
        await expect(
          sealMessage(keyring, { ...draft, recipients: list }),
          name,
        ).rejects.toBeInstanceOf(TypeError)
      }
    })

    it('RFC 9562 : un sentAt hors des 48 bits d’un UUIDv7, ou un aléa de 9 octets, ne scellent rien', async () => {
      const keyring = await senderKeyring()

      await expect(sealMessage(keyring, { ...draft, sentAt: 2 ** 48 })).rejects.toBeInstanceOf(
        MessageFormatError,
      )
      await expect(
        sealMessageWith(keyring, draft, {
          ...vectorRandomness(),
          messageIdRandom: new Uint8Array(9),
        }),
      ).rejects.toBeInstanceOf(RangeError)
    })
  })
}
