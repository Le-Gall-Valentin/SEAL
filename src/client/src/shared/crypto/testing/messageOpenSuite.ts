/**
 * CDC 7.7, PROTOCOL.md 6.5 (ADR 0030, points 4 et 7) : ouverture d'un message reçu, dans Chromium : la vérification du
 * certificat de l'émetteur lit l'Autorité épinglée dans IndexedDB. Les parties sont celles de messageParties.ts.
 * Réservé aux tests.
 */
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { ServedCertificate } from '../certificate'
import { CanonicalReader } from '../canonicalReader'
import { type Keyring } from '../keyring'
import { type KeyStore, openKeyStore, pinAuthority } from '../keystore'
import { fromBase64Url, toBase64Url } from '../base64url'
import { type EnvelopeWire, type WrapWire } from '../messageFormat'
import { openMessage } from '../openMessage'
import { chooseSubkey } from '../recipientKeys'
import { sealMessage } from '../sealMessage'
import { flipBase64Url, flipUuid } from './bytesForTests'
import { vectorCertificate, vectorEnvelope as readEnvelope } from './envelopeVectorSuite'
import { type MessageParties } from './messageParties'
import { allZero, watchDecrypt, watchSigned } from './subtleWatch'
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

    it('KEY-21 : après une rotation, l’ancienne et la nouvelle sous-clé s’ouvrent avec le trousseau qui a les deux', async () => {
      const store = await pinnedStore()
      const [sender, rotated] = [await parties.senderKeyring(1), await parties.senderKeyring(2)]
      const [older, newer] = rotated.subkeys
      if (older === undefined || newer === undefined) throw new Error('deux sous-clés attendues')

      for (const [subkey, sentAt] of [
        [older, older.createdAt + 86_400_000],
        [newer, newer.createdAt + 86_400_000],
      ] as const) {
        const own = await chooseSubkey(
          store,
          await parties.servedKeys(rotated, [subkey.ekId]),
          senderId,
          sentAt,
        )
        const bob = await chooseSubkey(
          store,
          await parties.servedKeys(await parties.bobKeyring()),
          bobId,
          sentAt,
        )
        const sealed = await sealMessage(sender, {
          conversationId: string(fields, 'conversationId'),
          conversationType: 1,
          senderCertSerial: string(fields, 'senderCertSerial'),
          sentAt,
          senderSeq: 4,
          groupSeq: 0,
          body: 'Après la rotation',
          recipients: [own, bob],
        })
        const context = { receivedAt: sentAt + 1_500 }

        expect((await read(rotated, sealed, context)).status).toBe('verified')
        // Le trousseau d'avant la rotation n'a que l'ancienne sous-clé.
        expect((await read(sender, sealed, context)).status).toBe(
          subkey === older ? 'verified' : 'undecryptable',
        )
      }
    })

    it('QA-04 : chaque falsification des vecteurs rend son statut, jamais verified', async () => {
      const bob = await parties.bobKeyring()
      const forgeries = records(envelope['forgeries'])
      expect(forgeries.length).toBeGreaterThan(8)
      for (const forgery of forgeries) {
        // Certaines falsifications portent le certificat que sert le serveur pour elles.
        const served = forgery['senderCertificate']
        const context =
          served === undefined ? {} : { senderCertificate: vectorCertificate(record(served)) }
        expect(
          (await read(bob, forgery['envelope'], context)).status,
          string(forgery, 'name'),
        ).toBe(string(forgery, 'rejectedBy'))
      }
    })

    it('CDC 7.7 : un message recipients_mismatch rend son contenu signé, à afficher sous l’avertissement ambre', async () => {
      const mismatch = records(envelope['forgeries']).find(
        (forgery) => forgery['rejectedBy'] === 'recipients_mismatch',
      )

      const result = await read(await parties.bobKeyring(), mismatch?.['envelope'])

      expect(result.status).toBe('recipients_mismatch')
      expect('message' in result ? result.message.body : undefined).toBe(string(fields, 'body'))
      expect('message' in result ? result.message.recipientIds : undefined).toEqual([senderId])
    })

    it('CDC 7.7, PROTOCOL.md 6.2 : à l’ouverture, la CEK, Plaintext et SignedPayload sont remis à zéro, refus compris', async () => {
      const bob = await parties.bobKeyring()
      const store = await pinnedStore()
      const forged = records(envelope['forgeries']).find(
        (forgery) => forgery['rejectedBy'] === 'signature_invalid',
      )
      for (const [name, sealed, status] of [
        ['message des vecteurs', vectorEnvelope, 'verified'],
        ['signature d’une autre IK', forged?.['envelope'], 'signature_invalid'],
      ] as const) {
        const [decrypted, signed] = [watchDecrypt(), watchSigned()]
        let result
        try {
          result = await openMessage(store, bob, sealed, {
            senderCertificate: certificate,
            receivedAt: RECEIVED_AT,
          })
        } finally {
          decrypted.restore()
          signed.restore()
        }

        expect(result.status, name).toBe(status)
        // La CEK, puis Plaintext ; SignedPayload, vérifié par l'IK du certificat.
        expect(decrypted.seen, name).toHaveLength(2)
        expect(allZero([...decrypted.seen, ...signed.seen]), name).toBe(true)
      }
    })

    it('CDC 7.7, PROTOCOL.md 6.2 : sur un refus, les champs relus de SignedPayload (sel, numéro de série) sont remis à zéro', async () => {
      const bob = await parties.bobKeyring()
      for (const [name, rejectedBy] of [
        [
          "le message signé par une autre clé d'identité que celle du certificat",
          'signature_invalid',
        ],
        ['le message signé rechiffré sous un autre numéro de certificat', 'integrity_failed'],
      ] as const) {
        const forged = records(envelope['forgeries']).find((forgery) => forgery['name'] === name)
        // Sans remplacement, l'espion laisse lire et garde chaque copie rendue ; mockRestore efface ce relevé.
        const copies = vi.spyOn(CanonicalReader.prototype, 'bytes')
        let result
        let seen: Uint8Array[] | undefined
        try {
          result = await openMessage(await pinnedStore(), bob, forged?.['envelope'], {
            senderCertificate: certificate,
            receivedAt: RECEIVED_AT,
          })
          seen = copies.mock.results.map((entry): Uint8Array =>
            entry.type === 'return' ? entry.value : new Uint8Array(1).fill(1),
          )
        } finally {
          copies.mockRestore()
        }

        expect(result.status, name).toBe(rejectedBy)
        expect(seen.length, name).toBeGreaterThan(0)
        expect(allZero(seen), name).toBe(true)
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

    it('QA-04 : chaque octet de l’en-tête, du nonce, du chiffré et de l’encapsulation de Bob, modifié, fait échouer la vérification', async () => {
      // Les encapsulations des autres destinataires ne regardent pas Bob : elles sont hors de l'AAD (CDC 7.3).
      const bob = await parties.bobKeyring()
      const store = await pinnedStore()
      const flipped = (text: string) => (index: number) => flipBase64Url(text, index)
      const header = (field: 'messageId' | 'conversationId' | 'senderId') => (index: number) => ({
        ...vectorEnvelope,
        [field]: flipUuid(vectorEnvelope[field], index),
      })
      const variants: [string, (index: number) => unknown, number][] = [
        ['messageId', header('messageId'), 16],
        ['conversationId', header('conversationId'), 16],
        ['senderId', header('senderId'), 16],
        [
          'numéro de série',
          (i) => ({
            ...vectorEnvelope,
            senderCertSerial: flipped(vectorEnvelope.senderCertSerial)(i),
          }),
          16,
        ],
        ['nonce', (i) => ({ ...vectorEnvelope, nonce: flipped(vectorEnvelope.nonce)(i) }), 12],
        [
          'chiffré',
          (i) => ({ ...vectorEnvelope, ciphertext: flipped(vectorEnvelope.ciphertext)(i) }),
          base64url(message, 'ciphertext').length,
        ],
        [
          'recipientId',
          (i) => withBobWrap((wrap) => ({ ...wrap, recipientId: flipUuid(wrap.recipientId, i) })),
          16,
        ],
        ...(['ekId', 'enc', 'wrappedCek'] as const).map(
          (field): [string, (index: number) => unknown, number] => [
            field,
            (i) => withBobWrap((wrap) => ({ ...wrap, [field]: flipBase64Url(wrap[field], i) })),
            fromBase64Url(bobWrap[field]).length,
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
          expect('message' in result, `${name}, octet ${String(index)}`).toBe(false)
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
          '51 encapsulations pour 51 destinataires distincts',
          {
            ...vectorEnvelope,
            wraps: [
              ...vectorEnvelope.wraps,
              ...Array.from({ length: 49 }, (_, index) => ({
                ...bobWrap,
                recipientId: `0192f7a1-ffff-7000-8000-${index.toString(16).padStart(12, '0')}`,
              })),
            ],
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
