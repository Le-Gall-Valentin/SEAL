/**
 * CDC 7.5 (étapes 3 à 7), PROTOCOL.md 6.4 : scellement d'un message, commun aux tests sous Node et dans Chromium.
 * L'émetteur et Bob sont ceux de messageParties.ts ; leurs sous-clés passent par chooseSubkey. Réservé aux tests.
 */
import { describe, expect, it } from 'vitest'
import { fromBase64Url } from '../base64url'
import { HpkeSealError, openCek } from '../hpke'
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
import { chooseSubkey, type ChosenSubkey } from '../recipientKeys'
import {
  type MessageDraft,
  MessageTooLongError,
  sealMessage,
  sealMessageWith,
  type SealRandomness,
} from '../sealMessage'
import { vectorX25519Private } from './envelopeVectorSuite'
import { authorityStore, type MessageParties } from './messageParties'
import { allZero, watchEncrypt, watchSigned } from './subtleWatch'
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

export function describeMessageSeal(envelope: Json, parties: MessageParties, engine: string): void {
  const message = record(envelope['message'])
  const fields = record(message['fields'])
  const wraps = records(message['wraps'])
  const bobJson =
    records(envelope['recipients']).find(
      (recipient) => string(recipient, 'userId') === parties.bobId,
    ) ?? {}
  const store = authorityStore(parties.authorityPublicKey)

  /** Le brouillon des vecteurs ; l'ordre des destinataires n'importe pas, le scellement les trie. */
  async function draft(changes: Partial<MessageDraft> = {}): Promise<MessageDraft> {
    const { own, bob } = await parties.chosen(store)
    return {
      conversationId: string(fields, 'conversationId'),
      conversationType: number(fields, 'conversationType') === 2 ? 2 : 1,
      senderCertSerial: string(fields, 'senderCertSerial'),
      sentAt: decimal(fields, 'sentAt'),
      senderSeq: decimal(fields, 'senderSeq'),
      groupSeq: decimal(fields, 'groupSeq'),
      body: string(fields, 'body'),
      recipients: [own, bob],
      ...changes,
    }
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

  /** La CEK et le SignedPayload d'une enveloppe, ouverts avec la clé privée de Bob, comme par un destinataire. */
  async function readAsBob(
    sealed: EnvelopeWire,
  ): Promise<{ readonly cek: Uint8Array; readonly payload: SignedPayloadFields }> {
    const wrap = sealed.wraps.find((candidate) => candidate.recipientId === parties.bobId)
    if (wrap === undefined) throw new Error('encapsulation de Bob attendue')
    const header = headerOf(sealed)
    const aad = encodeMessageAad(header)
    const cek = await openCek(
      {
        privateKey: await vectorX25519Private(base64url(bobJson, 'ekPrivate')),
        publicKey: base64url(bobJson, 'ekPublic'),
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
      expect(
        await sealMessageWith(await parties.senderKeyring(), await draft(), vectorRandomness()),
      ).toEqual(record(envelope['envelope']))
    })

    it('CDC 7.5, étape 8 : la CEK est remise à zéro une fois encapsulée', async () => {
      const randomness = vectorRandomness()

      await sealMessageWith(await parties.senderKeyring(), await draft(), randomness)

      expect(randomness.cek.every((byte) => byte === 0)).toBe(true)
    })

    it('PROTOCOL.md 6.2 : SignedPayload, Plaintext et chaque copie de la CEK sont remis à zéro après le scellement', async () => {
      const keyring = await parties.senderKeyring()
      const vector = await draft()
      const [encrypted, signed] = [watchEncrypt(), watchSigned()]
      try {
        await sealMessage(keyring, vector)
      } finally {
        encrypted.restore()
        signed.restore()
      }

      // Plaintext, puis la CEK de chacun des deux destinataires ; SignedPayload, signé par l'IK.
      expect(encrypted.seen).toHaveLength(3)
      expect(signed.seen).toHaveLength(1)
      expect(allZero([...encrypted.seen, ...signed.seen])).toBe(true)
    })

    it('CDC 7.5 : messageId est un UUIDv7 à sentAt ; deux scellements tirent un sel, une CEK et un nonce neufs', async () => {
      const keyring = await parties.senderKeyring()
      const vector = await draft()
      const [one, two] = [await sealMessage(keyring, vector), await sealMessage(keyring, vector)]
      const hex = one.messageId.replaceAll('-', '')

      expect(Number.parseInt(hex.slice(0, 12), 16)).toBe(vector.sentAt)
      expect(hex[12]).toBe('7')
      expect(['8', '9', 'a', 'b']).toContain(hex[16])
      expect(two.messageId).not.toBe(one.messageId)
      expect(two.nonce).not.toBe(one.nonce)
      const [readOne, readTwo] = [await readAsBob(one), await readAsBob(two)]
      expect(readTwo.cek).not.toEqual(readOne.cek)
      expect(readTwo.payload.salt).not.toEqual(readOne.payload.salt)
      expect(readOne.payload.recipientIds).toEqual([parties.bobId, parties.senderId])
    })

    it('ADR 0030, point 6 : 4 000 points de code, émojis compris, passent ; 4 001 sont refusés ; le corps est mis en NFC', async () => {
      const keyring = await parties.senderKeyring()

      const sealed = await sealMessage(keyring, await draft({ body: `${'🦭'.repeat(3999)}é` }))
      expect((await readAsBob(sealed)).payload.body).toBe(`${'🦭'.repeat(3999)}é`)
      await expect(
        sealMessage(keyring, await draft({ body: '🦭'.repeat(4001) })),
      ).rejects.toBeInstanceOf(MessageTooLongError)
    })

    it('CDC 7.4 : un destinataire en double, l’émetteur absent ou plus de 50 destinataires sont refusés', async () => {
      const keyring = await parties.senderKeyring()
      const { own, bob } = await parties.chosen(store)

      for (const [list, rule] of [
        [[own, bob, bob], 'CDC 7.4 : un destinataire en double'],
        [[bob], 'CDC 7.4 : l’émetteur figure parmi les destinataires'],
        [[own, ...Array.from({ length: 50 }, () => bob)], 'MSG-21 : 50 destinataires au plus'],
      ] as const) {
        await expect(sealMessage(keyring, await draft({ recipients: list }))).rejects.toThrow(
          new TypeError(rule),
        )
      }
    })

    it('ADR 0030, point 8 : une sous-clé construite à la main, sans chooseSubkey, n’est pas scellée', async () => {
      const { own, bob } = await parties.chosen(store)
      const forged: ChosenSubkey = {
        recipientId: bob.recipientId,
        certSerial: Uint8Array.from(bob.certSerial),
        ikPublic: Uint8Array.from(bob.ikPublic),
        ekId: Uint8Array.from(bob.ekId),
        ekPublic: Uint8Array.from(bob.ekPublic),
      }

      await expect(
        sealMessage(await parties.senderKeyring(), await draft({ recipients: [own, forged] })),
      ).rejects.toBeInstanceOf(TypeError)
    })

    it('ADR 0030, point 8 : modifier une sous-clé choisie ne change rien, le scellement suit la copie vérifiée', async () => {
      const { own, bob } = await parties.chosen(store)
      bob.ekPublic.fill(0)

      const sealed = await sealMessage(
        await parties.senderKeyring(),
        await draft({ recipients: [own, bob] }),
      )

      expect((await readAsBob(sealed)).payload.body).toBe(string(fields, 'body'))
    })

    it('RFC 9180, 7.1.4 : une sous-clé d’ordre faible, pourtant bien liée, n’est pas scellée', async () => {
      const weak = await parties.bobKeyring(new Uint8Array(32))
      const bob = await chooseSubkey(
        store,
        await parties.servedKeys(weak),
        parties.bobId,
        decimal(fields, 'sentAt'),
      )
      const { own } = await parties.chosen(store)

      await expect(
        sealMessage(await parties.senderKeyring(), await draft({ recipients: [own, bob] })),
      ).rejects.toBeInstanceOf(HpkeSealError)
    })

    it('PROTOCOL.md 6.2 : un senderSeq nul ou un groupSeq non nul en direct ne scellent rien', async () => {
      const keyring = await parties.senderKeyring()

      for (const changes of [{ senderSeq: 0 }, { groupSeq: 1 }]) {
        await expect(sealMessage(keyring, await draft(changes))).rejects.toBeInstanceOf(
          MessageFormatError,
        )
      }
    })

    it('RFC 9562 : un sentAt hors des 48 bits d’un UUIDv7, ou un aléa de 9 octets, ne scellent rien', async () => {
      const keyring = await parties.senderKeyring()

      await expect(sealMessage(keyring, await draft({ sentAt: 2 ** 48 }))).rejects.toBeInstanceOf(
        MessageFormatError,
      )
      await expect(
        sealMessageWith(keyring, await draft(), {
          ...vectorRandomness(),
          messageIdRandom: new Uint8Array(9),
        }),
      ).rejects.toBeInstanceOf(RangeError)
    })
  })
}
