/**
 * CDC 7.7, PROTOCOL.md 6.5 (ADR 0030, points 4 et 7) : ouverture d'un message reçu, dans Chromium : la vérification du
 * certificat de l'émetteur lit l'Autorité épinglée dans IndexedDB. Les parties sont celles de messageParties.ts.
 * Réservé aux tests.
 */
import { afterEach, describe, expect, it } from 'vitest'
import type { ServedCertificate } from '../certificate'
import { type Keyring } from '../keyring'
import { type KeyStore, openKeyStore, pinAuthority } from '../keystore'
import { toBase64Url } from '../base64url'
import { type EnvelopeWire, type WrapWire } from '../messageFormat'
import { openMessage } from '../openMessage'
import { sealMessage } from '../sealMessage'
import { flipBase64Url } from './bytesForTests'
import { vectorEnvelope as readEnvelope } from './envelopeVectorSuite'
import { type MessageParties } from './messageParties'
import { base64url, decimal, type Json, record, records, string } from './vectorReader'

export function describeMessageOpen(envelope: Json, parties: MessageParties, engine: string): void {
  const message = record(envelope['message'])
  const fields = record(message['fields'])
  const { senderId, bobId } = parties
  const certificate = parties.senderCertificate
  const RECEIVED_AT = parties.receivedAt

  const opened: { store: KeyStore; name: string }[] = []

  async function pinnedStore(): Promise<KeyStore> {
    const name = `seal-test-${crypto.randomUUID()}`
    const store = await openKeyStore(name)
    opened.push({ store, name })
    await pinAuthority(store, parties.authorityPublicKey)
    return store
  }

  afterEach(async () => {
    for (const { store, name } of opened.splice(0)) {
      store.close()
      await new Promise((resolve) => {
        indexedDB.deleteDatabase(name).onsuccess = resolve
      })
    }
  })

  async function read(
    keyring: Keyring,
    sealed: unknown,
    context: { senderCertificate?: ServedCertificate; receivedAt?: number } = {},
  ) {
    return openMessage(await pinnedStore(), keyring, sealed, {
      senderCertificate: context.senderCertificate ?? certificate,
      receivedAt: context.receivedAt ?? RECEIVED_AT,
    })
  }

  const vectorEnvelope: EnvelopeWire = readEnvelope(record(envelope['envelope']))
  const bobWrap = vectorEnvelope.wraps.find((wrap) => wrap.recipientId === bobId)
  if (bobWrap === undefined) throw new Error('encapsulation de Bob attendue')

  /** L'enveloppe des vecteurs, l'encapsulation de Bob remplacée par `change(…)`. */
  function withBobWrap(change: (wrap: WrapWire) => unknown): unknown {
    return {
      ...vectorEnvelope,
      wraps: vectorEnvelope.wraps.map((wrap) => (wrap === bobWrap ? change(wrap) : wrap)),
    }
  }

  describe(`CDC 7.7 : ouverture d'un message reçu (${engine})`, () => {
    it('CDC 7.7 : Bob ouvre le message des vecteurs, vérifié, avec son contenu signé', async () => {
      const result = await read(await parties.bobKeyring(), vectorEnvelope)

      expect(result.status).toBe('verified')
      if (result.status !== 'verified') return
      expect(result.message.body).toBe(string(fields, 'body'))
      expect(result.message.senderId).toBe(senderId)
      expect(result.message.recipientIds).toEqual([bobId, senderId])
      expect(result.message.senderIkPublic).toEqual(
        base64url(record(record(envelope['sender'])['certificate']), 'ikPublic'),
      )
      expect(result.message.signedPayload).toEqual(base64url(message, 'signedPayload'))
      expect(result.message.signature).toEqual(base64url(message, 'signature'))
    })

    it('CDC 7.4 : l’émetteur relit son propre message, sur un autre appareil', async () => {
      expect((await read(await parties.senderKeyring(), vectorEnvelope)).status).toBe('verified')
    })

    it('CDC 7.5 et 7.7 : aller-retour de l’émetteur à Bob, avec un aléa neuf', async () => {
      const sealed = await sealMessage(await parties.senderKeyring(), {
        conversationId: string(fields, 'conversationId'),
        conversationType: 1,
        senderCertSerial: string(fields, 'senderCertSerial'),
        sentAt: decimal(fields, 'sentAt'),
        senderSeq: 2,
        groupSeq: 0,
        body: 'Bien reçu 🦭',
        recipients: Object.values(await parties.chosen(await pinnedStore())),
      })

      const result = await read(await parties.bobKeyring(), sealed)

      expect(result.status).toBe('verified')
      expect(result.status === 'verified' ? result.message.body : undefined).toBe('Bien reçu 🦭')
    })

    it('QA-04 : chaque falsification des vecteurs rend son statut, jamais verified', async () => {
      const bob = await parties.bobKeyring()
      const forgeries = records(envelope['forgeries'])
      expect(forgeries.length).toBeGreaterThan(8)
      for (const forgery of forgeries) {
        expect((await read(bob, forgery['envelope'])).status, string(forgery, 'name')).toBe(
          string(forgery, 'rejectedBy'),
        )
      }
    })

    it('ADR 0030, point 7 : un padding non vide, jusqu’à 24 576 octets de chiffré (MSG-21), est accepté et ignoré', async () => {
      const bob = await parties.bobKeyring()
      const accepted = records(envelope['accepted'])
      expect(accepted.length).toBeGreaterThan(1)

      for (const vector of accepted) {
        expect((await read(bob, vector['envelope'])).status, string(vector, 'name')).toBe(
          'verified',
        )
      }
    })

    it('QA-04 : chaque octet du nonce, du chiffré, de enc, de la CEK encapsulée et du numéro de série, inversé, échoue', async () => {
      const bob = await parties.bobKeyring()
      const store = await pinnedStore()
      const wrapIndex = vectorEnvelope.wraps.findIndex((wrap) => wrap.recipientId === bobId)
      const variants: [string, (index: number) => EnvelopeWire, number][] = [
        [
          'nonce',
          (i) => ({ ...vectorEnvelope, nonce: flipBase64Url(vectorEnvelope.nonce, i) }),
          12,
        ],
        [
          'chiffré',
          (i) => ({ ...vectorEnvelope, ciphertext: flipBase64Url(vectorEnvelope.ciphertext, i) }),
          base64url(message, 'ciphertext').length,
        ],
        [
          'numéro de série',
          (i) => ({
            ...vectorEnvelope,
            senderCertSerial: flipBase64Url(vectorEnvelope.senderCertSerial, i),
          }),
          16,
        ],
        ...(['enc', 'wrappedCek'] as const).map(
          (field): [string, (index: number) => EnvelopeWire, number] => [
            field,
            (i) => ({
              ...vectorEnvelope,
              wraps: vectorEnvelope.wraps.map((wrap, index) =>
                index === wrapIndex ? { ...wrap, [field]: flipBase64Url(wrap[field], i) } : wrap,
              ),
            }),
            field === 'enc' ? 32 : 48,
          ],
        ),
      ]
      for (const [name, forge, length] of variants) {
        for (let index = 0; index < length; index += 1) {
          const result = await openMessage(store, bob, forge(index), {
            senderCertificate: certificate,
            receivedAt: RECEIVED_AT,
          })
          expect(result.status, `${name}, octet ${String(index)}`).not.toBe('verified')
        }
      }
    })

    it('CDC 7.7, étape 6 : le certificat est jugé à serverReceivedAt, et refusé hors de sa validité', async () => {
      const bob = await parties.bobKeyring()

      expect((await read(bob, vectorEnvelope, { receivedAt: certificate.notAfter })).status).toBe(
        'signature_invalid',
      )
      expect(
        (await read(bob, vectorEnvelope, { receivedAt: certificate.notBefore - 1 })).status,
      ).toBe('signature_invalid')
      expect(
        (await read(bob, vectorEnvelope, { senderCertificate: { ...certificate, usage: 1 } }))
          .status,
      ).toBe('signature_invalid')
    })

    it('CDC 7.7, étape 6 : un serverReceivedAt qui n’est pas un entier refuse le certificat au lieu d’en sauter la validité', async () => {
      const bob = await parties.bobKeyring()

      for (const receivedAt of [Number.NaN, RECEIVED_AT + 0.5]) {
        expect((await read(bob, vectorEnvelope, { receivedAt })).status, String(receivedAt)).toBe(
          'signature_invalid',
        )
      }
    })

    it('CDC 7.7, étape 3 : sans encapsulation pour soi, le message est illisible sur cet appareil', async () => {
      const { own } = await parties.chosen(await pinnedStore())
      const sealed = await sealMessage(await parties.senderKeyring(), {
        conversationId: string(fields, 'conversationId'),
        conversationType: 1,
        senderCertSerial: string(fields, 'senderCertSerial'),
        sentAt: decimal(fields, 'sentAt'),
        senderSeq: 3,
        groupSeq: 0,
        body: 'Note pour moi',
        recipients: [own],
      })

      expect((await read(await parties.bobKeyring(), sealed)).status).toBe('undecryptable')
    })

    it('CDC 7.7, étape 1 : une enveloppe illisible (schéma, version, suite, base64url, tailles, MSG-21) est refusée', async () => {
      const bob = await parties.bobKeyring()
      const withoutNonce = Object.fromEntries(
        Object.entries(vectorEnvelope).filter(([key]) => key !== 'nonce'),
      )
      const short = toBase64Url(new Uint8Array(15))
      for (const [name, forged] of [
        ['enveloppe nulle', null],
        ['champ manquant', withoutNonce],
        ['champ inconnu', { ...vectorEnvelope, extra: '' }],
        ['champ inconnu dans une encapsulation', withBobWrap((wrap) => ({ ...wrap, extra: '' }))],
        ['encapsulations absentes', { ...vectorEnvelope, wraps: null }],
        ['version 2', { ...vectorEnvelope, v: 2 }],
        ['version booléenne', { ...vectorEnvelope, v: true }],
        ['autre suite', { ...vectorEnvelope, suite: 'SEAL1-XWING-A256GCM-ED25519' }],
        ['messageId hors de la forme UUID', { ...vectorEnvelope, messageId: 'message' }],
        [
          'conversationId en majuscules',
          { ...vectorEnvelope, conversationId: vectorEnvelope.conversationId.toUpperCase() },
        ],
        ['senderId en majuscules', { ...vectorEnvelope, senderId: senderId.toUpperCase() }],
        [
          'recipientId en majuscules',
          withBobWrap((wrap) => ({ ...wrap, recipientId: wrap.recipientId.toUpperCase() })),
        ],
        ['nonce non canonique', { ...vectorEnvelope, nonce: `${vectorEnvelope.nonce}=` }],
        ['nonce de 11 octets', { ...vectorEnvelope, nonce: 'AAAAAAAAAAAAAAA' }],
        ['numéro de série de 15 octets', { ...vectorEnvelope, senderCertSerial: short }],
        ['ekId de 15 octets', withBobWrap((wrap) => ({ ...wrap, ekId: short }))],
        [
          'enc de 31 octets',
          withBobWrap((wrap) => ({ ...wrap, enc: toBase64Url(new Uint8Array(31)) })),
        ],
        [
          'wrappedCek de 47 octets',
          withBobWrap((wrap) => ({ ...wrap, wrappedCek: toBase64Url(new Uint8Array(47)) })),
        ],
        ['chiffré plus court que son étiquette', { ...vectorEnvelope, ciphertext: 'AAAA' }],
        [
          'chiffré de plus de 24 576 octets',
          { ...vectorEnvelope, ciphertext: toBase64Url(new Uint8Array(24_577)) },
        ],
        [
          '52 encapsulations',
          {
            ...vectorEnvelope,
            wraps: Array.from({ length: 26 }, () => vectorEnvelope.wraps).flat(),
          },
        ],
        [
          'deux encapsulations pour Bob',
          { ...vectorEnvelope, wraps: [...vectorEnvelope.wraps, bobWrap] },
        ],
      ] as const) {
        expect((await read(bob, forged)).status, name).toBe('integrity_failed')
      }
    })

    it('CDC 7.7, étape 6 : un certificat valide de l’émetteur, mais d’un autre numéro de série, est refusé', async () => {
      const other = await parties.signedByCa({ ...certificate, serial: 'MDEyMzQ1Njc4OTo7PD0-Pw' })

      expect(
        (await read(await parties.bobKeyring(), vectorEnvelope, { senderCertificate: other }))
          .status,
      ).toBe('signature_invalid')
    })

    it('A10 : une panne du trousseau de l’appareil remonte telle quelle, sans statut', async () => {
      const store = await pinnedStore()
      const broken: KeyStore = {
        ...store,
        loadAuthority: () => Promise.reject(new Error('IndexedDB indisponible')),
      }

      await expect(
        openMessage(broken, await parties.bobKeyring(), vectorEnvelope, {
          senderCertificate: certificate,
          receivedAt: RECEIVED_AT,
        }),
      ).rejects.toThrow('IndexedDB indisponible')
    })
  })
}
