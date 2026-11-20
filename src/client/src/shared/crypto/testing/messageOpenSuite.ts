/**
 * CDC 7.7, PROTOCOL.md 6.5 (ADR 0030, points 4 et 7) : ouverture d'un message reçu, commune aux tests sous Node et
 * dans Chromium. Bob lit avec la clé privée de Bob de la RFC 7748 ; l'émetteur, avec le trousseau du coffre de
 * vault.json. Réservé aux tests.
 */
import { afterEach, describe, expect, it } from 'vitest'
import { fromBase64Url, toBase64Url } from '../base64url'
import { concat } from '../canonical'
import { encodeCertificate } from '../identity'
import { flipBase64Url } from './bytesForTests'
import type { ServedCertificate } from '../certificate'
import { createKeyring, type Keyring, openVault } from '../keyring'
import { type KeyStore, openKeyStore, pinAuthority } from '../keystore'
import { type EnvelopeWire } from '../messageFormat'
import { openMessage } from '../openMessage'
import { sealMessage } from '../sealMessage'
import type { ChosenSubkey } from '../recipientKeys'
import { vectorX25519Private } from './envelopeVectorSuite'
import { vaultPayload, vectorRecoveryKey } from './vaultVectorSuite'
import { base64url, decimal, type Json, number, record, records, string } from './vectorReader'

export function describeMessageOpen(
  envelope: Json,
  vault: Json,
  identity: Json,
  engine: string,
): void {
  const message = record(envelope['message'])
  const fields = record(message['fields'])
  const sender = record(envelope['sender'])
  const certificateJson = record(sender['certificate'])
  const recipients = records(envelope['recipients'])
  const senderId = string(sender, 'userId')
  const bobJson = recipients.find((recipient) => string(recipient, 'userId') !== senderId) ?? {}
  const bobId = string(bobJson, 'userId')
  const CA_PUBLIC = base64url(record(record(identity['keys'])['ca']), 'publicKey')
  const RECEIVED_AT = decimal(fields, 'sentAt') + 1_500

  const certificate: ServedCertificate = {
    serial: string(certificateJson, 'serial'),
    issuerKeyId: string(certificateJson, 'issuerKeyId'),
    subjectId: string(certificateJson, 'subjectId'),
    username: string(certificateJson, 'username'),
    ikPublic: string(certificateJson, 'ikPublic'),
    notBefore: decimal(certificateJson, 'notBefore'),
    notAfter: decimal(certificateJson, 'notAfter'),
    usage: number(certificateJson, 'usage'),
    signature: string(certificateJson, 'signature'),
  }

  const opened: { store: KeyStore; name: string }[] = []

  async function pinnedStore(): Promise<KeyStore> {
    const name = `seal-test-${crypto.randomUUID()}`
    const store = await openKeyStore(name)
    opened.push({ store, name })
    await pinAuthority(store, CA_PUBLIC)
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

  async function senderKeyring(): Promise<Keyring> {
    return openVault(senderId, vaultPayload(vault['payload']), vectorRecoveryKey(vault))
  }

  /** Trousseau de Bob : sa seule sous-clé (EK de Bob, RFC 7748) compte ici ; son IK n'est pas lue à l'ouverture. */
  async function bobKeyring(): Promise<Keyring> {
    const identity = await crypto.subtle.generateKey({ name: 'Ed25519' }, false, ['sign', 'verify'])
    return createKeyring(
      {
        userId: bobId,
        identityPublicKey: new Uint8Array(32),
        subkeys: [
          {
            ekId: base64url(bobJson, 'ekId'),
            kem: 'DHKEM-X25519-HKDF-SHA256',
            publicKey: base64url(bobJson, 'ekPublic'),
            createdAt: certificate.notBefore,
            expiresAt: certificate.notBefore + 45 * 86_400_000,
          },
        ],
        createdAt: certificate.notBefore,
      },
      {
        identityKey: identity.privateKey,
        subkeyKeys: [await vectorX25519Private(base64url(bobJson, 'ekPrivate'))],
        vdk: await crypto.subtle.generateKey({ name: 'AES-GCM', length: 256 }, false, [
          'encrypt',
          'decrypt',
        ]),
      },
    )
  }

  function chosenFor(recipient: Json): ChosenSubkey {
    return {
      recipientId: string(recipient, 'userId'),
      certSerial: base64url(certificateJson, 'serial'),
      ikPublic: base64url(certificateJson, 'ikPublic'),
      ekId: base64url(recipient, 'ekId'),
      ekPublic: base64url(recipient, 'ekPublic'),
    }
  }

  async function read(
    keyring: Keyring,
    sealed: EnvelopeWire,
    context: { senderCertificate?: ServedCertificate; receivedAt?: number } = {},
  ) {
    return openMessage(await pinnedStore(), keyring, sealed, {
      senderCertificate: context.senderCertificate ?? certificate,
      receivedAt: context.receivedAt ?? RECEIVED_AT,
    })
  }

  const vectorEnvelope = record(envelope['envelope']) as unknown as EnvelopeWire

  describe(`CDC 7.7 : ouverture d'un message reçu (${engine})`, () => {
    it('CDC 7.7 : Bob ouvre le message des vecteurs, vérifié, avec son contenu signé', async () => {
      const result = await read(await bobKeyring(), vectorEnvelope)

      expect(result.status).toBe('verified')
      if (result.status !== 'verified') return
      expect(result.message.body).toBe(string(fields, 'body'))
      expect(result.message.senderId).toBe(senderId)
      expect(result.message.recipientIds).toEqual([bobId, senderId])
      expect(result.message.senderIkPublic).toEqual(base64url(certificateJson, 'ikPublic'))
      expect(result.message.signedPayload).toEqual(base64url(message, 'signedPayload'))
      expect(result.message.signature).toEqual(base64url(message, 'signature'))
    })

    it('CDC 7.4 : l’émetteur relit son propre message, sur un autre appareil', async () => {
      expect((await read(await senderKeyring(), vectorEnvelope)).status).toBe('verified')
    })

    it('CDC 7.5 et 7.7 : aller-retour de l’émetteur à Bob, avec un aléa neuf', async () => {
      const sealed = await sealMessage(await senderKeyring(), {
        conversationId: string(fields, 'conversationId'),
        conversationType: 1,
        senderCertSerial: string(fields, 'senderCertSerial'),
        sentAt: decimal(fields, 'sentAt'),
        senderSeq: 2,
        groupSeq: 0,
        body: 'Bien reçu 🦭',
        recipients: recipients.map(chosenFor),
      })

      const result = await read(await bobKeyring(), sealed)

      expect(result.status).toBe('verified')
      expect(result.status === 'verified' ? result.message.body : undefined).toBe('Bien reçu 🦭')
    })

    it('QA-04 : chaque falsification des vecteurs rend son statut, jamais verified', async () => {
      const bob = await bobKeyring()
      const forgeries = records(envelope['forgeries'])
      expect(forgeries.length).toBeGreaterThan(8)
      for (const forgery of forgeries) {
        expect(
          (await read(bob, record(forgery['envelope']) as unknown as EnvelopeWire)).status,
          string(forgery, 'name'),
        ).toBe(string(forgery, 'rejectedBy'))
      }
    })

    it('ADR 0030, point 7 : un padding non vide est accepté et ignoré', async () => {
      const accepted = records(envelope['accepted'])[0] ?? {}

      expect(
        (await read(await bobKeyring(), record(accepted['envelope']) as unknown as EnvelopeWire))
          .status,
      ).toBe('verified')
    })

    it('QA-04 : chaque octet du nonce, du chiffré, de enc, de la CEK encapsulée et du numéro de série, inversé, échoue', async () => {
      const bob = await bobKeyring()
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
      const bob = await bobKeyring()

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
      const bob = await bobKeyring()

      for (const receivedAt of [Number.NaN, RECEIVED_AT + 0.5]) {
        expect((await read(bob, vectorEnvelope, { receivedAt })).status, String(receivedAt)).toBe(
          'signature_invalid',
        )
      }
    })

    it('CDC 7.7, étape 3 : sans encapsulation pour soi, le message est illisible sur cet appareil', async () => {
      const own = recipients.find((recipient) => string(recipient, 'userId') === senderId) ?? {}
      const sealed = await sealMessage(await senderKeyring(), {
        conversationId: string(fields, 'conversationId'),
        conversationType: 1,
        senderCertSerial: string(fields, 'senderCertSerial'),
        sentAt: decimal(fields, 'sentAt'),
        senderSeq: 3,
        groupSeq: 0,
        body: 'Note pour moi',
        recipients: [chosenFor(own)],
      })

      expect((await read(await bobKeyring(), sealed)).status).toBe('undecryptable')
    })

    it('CDC 7.7, étape 1 : une enveloppe illisible (version, suite, base64url, tailles) est refusée', async () => {
      const bob = await bobKeyring()
      for (const [name, forged] of [
        ['version 2', { ...vectorEnvelope, v: 2 }],
        ['autre suite', { ...vectorEnvelope, suite: 'SEAL1-XWING-A256GCM-ED25519' }],
        ['nonce non canonique', { ...vectorEnvelope, nonce: `${vectorEnvelope.nonce}=` }],
        ['messageId hors de la forme UUID', { ...vectorEnvelope, messageId: 'message' }],
        ['nonce de 11 octets', { ...vectorEnvelope, nonce: 'AAAAAAAAAAAAAAA' }],
        ['chiffré plus court que son étiquette', { ...vectorEnvelope, ciphertext: 'AAAA' }],
        [
          '52 encapsulations',
          {
            ...vectorEnvelope,
            wraps: Array.from({ length: 26 }, () => vectorEnvelope.wraps).flat(),
          },
        ],
      ] as const) {
        expect((await read(bob, forged)).status, name).toBe('integrity_failed')
      }
    })

    it('CDC 7.7, étape 6 : un certificat valide de l’émetteur, mais d’un autre numéro de série, est refusé', async () => {
      const other = await signedByCa({ ...certificate, serial: 'MDEyMzQ1Njc4OTo7PD0-Pw' })

      expect(
        (await read(await bobKeyring(), vectorEnvelope, { senderCertificate: other })).status,
      ).toBe('signature_invalid')
    })

    it('A10 : une panne du trousseau de l’appareil remonte telle quelle, sans statut', async () => {
      const store = await pinnedStore()
      const broken: KeyStore = {
        ...store,
        loadAuthority: () => Promise.reject(new Error('IndexedDB indisponible')),
      }

      await expect(
        openMessage(broken, await bobKeyring(), vectorEnvelope, {
          senderCertificate: certificate,
          receivedAt: RECEIVED_AT,
        }),
      ).rejects.toThrow('IndexedDB indisponible')
    })
  })

  /** Un certificat signé comme par la CA des vecteurs (graine publique du test 1 de la RFC 8032). */
  async function signedByCa(served: ServedCertificate): Promise<ServedCertificate> {
    const pkcs8 = concat(
      Uint8Array.of(
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
        0x70,
        0x04,
        0x22,
        0x04,
        0x20,
      ),
      base64url(record(record(identity['keys'])['ca']), 'seed'),
    )
    const key = await crypto.subtle.importKey('pkcs8', pkcs8, 'Ed25519', false, ['sign'])
    const encoded = encodeCertificate({
      serial: fromBase64Url(served.serial),
      issuerKeyId: fromBase64Url(served.issuerKeyId),
      subjectId: served.subjectId,
      username: served.username,
      ikPublic: fromBase64Url(served.ikPublic),
      notBefore: served.notBefore,
      notAfter: served.notAfter,
      usage: served.usage,
    })
    return {
      ...served,
      signature: toBase64Url(new Uint8Array(await crypto.subtle.sign('Ed25519', key, encoded))),
    }
  }
}
