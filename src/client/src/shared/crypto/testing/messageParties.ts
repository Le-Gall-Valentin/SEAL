/**
 * Les deux parties des vecteurs de envelope.json, communes aux tests sous Node et dans Chromium :
 * - l'émetteur : trousseau du coffre de vault.json, certificat de identity.json ;
 * - Bob : sous-clé des vecteurs, avec une IK et un certificat créés pour le test, signés par l'Autorité des vecteurs.
 * Leurs sous-clés passent par chooseSubkey, comme au lot 4 (ADR 0030, point 8). Réservé aux tests.
 */
import { fromBase64Url, toBase64Url } from '../base64url'
import { concat } from '../canonical'
import type { ServedCertificate } from '../certificate'
import { encodeCertificate, KEM } from '../identity'
import { createKeyring, type Keyring, openVault } from '../keyring'
import type { KeyStore } from '../keystore'
import { SUBKEY_VALIDITY_MS } from '../protocol'
import { bindSubkey } from '../publication'
import { type ChosenSubkey, chooseSubkey, type ServedUserKeys } from '../recipientKeys'
import { vectorCertificate, vectorX25519Private } from './envelopeVectorSuite'
import { vaultPayload, vectorRecoveryKey } from './vaultVectorSuite'
import { base64url, decimal, type Json, record, records, string } from './vectorReader'

/** RFC 8410 : en-tête PKCS#8 d'une graine Ed25519 brute de 32 octets. */
const ED25519_PKCS8_PREFIX = Uint8Array.of(
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
)
/** Numéro de série du certificat de Bob, distinct de celui de l'émetteur. */
const BOB_SERIAL = toBase64Url(Uint8Array.from({ length: 16 }, (_, index) => 0x70 + index))

export interface MessageParties {
  readonly senderId: string
  readonly bobId: string
  /** Certificat de l'émetteur, tel que le sert le serveur. */
  readonly senderCertificate: ServedCertificate
  /** Clé publique de l'Autorité des vecteurs, à épingler. */
  readonly authorityPublicKey: Uint8Array<ArrayBuffer>
  /** `serverReceivedAt` du message des vecteurs. */
  readonly receivedAt: number
  /** Coffre de vault.json : version 1 (sous-clé A0…AF), ou version 2 (A0…AF, puis B0…BF trente jours plus tard). */
  senderKeyring(version?: 1 | 2): Promise<Keyring>
  /** Trousseau de Bob ; `ekPublic` remplace la clé publique liée, pour forger une sous-clé signée. */
  bobKeyring(ekPublic?: Uint8Array<ArrayBuffer>): Promise<Keyring>
  /** GET /api/v1/users/{id}/keys pour ce trousseau : son certificat, et la liaison de chaque sous-clé désignée. */
  servedKeys(keyring: Keyring, ekIds?: readonly Uint8Array[]): Promise<ServedUserKeys>
  /** chooseSubkey pour l'émetteur et pour Bob, à l'instant `now` (par défaut, le sentAt des vecteurs). */
  chosen(
    store: KeyStore,
    now?: number,
  ): Promise<{ readonly own: ChosenSubkey; readonly bob: ChosenSubkey }>
  /** Le certificat donné, signé comme par l'Autorité des vecteurs. */
  signedByCa(served: ServedCertificate): Promise<ServedCertificate>
}

/** Un trousseau qui ne connaît que l'Autorité épinglée : chooseSubkey n'en lit rien d'autre. */
export function authorityStore(publicKey: Uint8Array): KeyStore {
  return {
    loadKeyring: () => Promise.resolve(undefined),
    saveKeyring: () => Promise.reject(new Error('trousseau de test en lecture seule')),
    loadCertificate: () => Promise.resolve(undefined),
    loadAuthority: () => Promise.resolve(Uint8Array.from(publicKey)),
    close: () => undefined,
  }
}

export async function messageParties(
  envelope: Json,
  vault: Json,
  identity: Json,
): Promise<MessageParties> {
  const sender = record(envelope['sender'])
  const certificateJson = record(sender['certificate'])
  const senderId = string(sender, 'userId')
  const bobJson =
    records(envelope['recipients']).find((recipient) => string(recipient, 'userId') !== senderId) ??
    {}
  const bobId = string(bobJson, 'userId')
  const ca = record(record(identity['keys'])['ca'])
  const caKey = await crypto.subtle.importKey(
    'pkcs8',
    concat(ED25519_PKCS8_PREFIX, base64url(ca, 'seed')),
    'Ed25519',
    false,
    ['sign'],
  )
  const senderCertificate = vectorCertificate(certificateJson)
  const sentAt = decimal(record(record(envelope['message'])['fields']), 'sentAt')
  const receivedAt = decimal(envelope, 'receivedAt')
  const bobIdentity = await crypto.subtle.generateKey({ name: 'Ed25519' }, false, [
    'sign',
    'verify',
  ])
  const bobIkPublic = new Uint8Array(await crypto.subtle.exportKey('raw', bobIdentity.publicKey))

  async function signedByCa(served: ServedCertificate): Promise<ServedCertificate> {
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
      signature: toBase64Url(new Uint8Array(await crypto.subtle.sign('Ed25519', caKey, encoded))),
    }
  }

  const bobCertificate = await signedByCa({
    ...senderCertificate,
    serial: BOB_SERIAL,
    subjectId: bobId,
    username: 'bob',
    ikPublic: toBase64Url(bobIkPublic),
  })

  async function senderKeyring(version: 1 | 2 = 1): Promise<Keyring> {
    const payload = version === 1 ? vault['payload'] : record(vault['nextVersion'])['payload']
    return openVault(senderId, vaultPayload(payload), vectorRecoveryKey(vault))
  }

  async function bobKeyring(ekPublic = base64url(bobJson, 'ekPublic')): Promise<Keyring> {
    return createKeyring(
      {
        userId: bobId,
        identityPublicKey: bobIkPublic,
        subkeys: [
          {
            ekId: base64url(bobJson, 'ekId'),
            kem: KEM,
            publicKey: ekPublic,
            createdAt: senderCertificate.notBefore,
            expiresAt: senderCertificate.notBefore + SUBKEY_VALIDITY_MS,
          },
        ],
        createdAt: senderCertificate.notBefore,
      },
      {
        identityKey: bobIdentity.privateKey,
        subkeyKeys: [await vectorX25519Private(base64url(bobJson, 'ekPrivate'))],
        vdk: await crypto.subtle.generateKey({ name: 'AES-GCM', length: 256 }, false, [
          'encrypt',
          'decrypt',
        ]),
      },
    )
  }

  async function servedKeys(
    keyring: Keyring,
    ekIds: readonly Uint8Array[] = keyring.subkeys.map((subkey) => subkey.ekId),
  ): Promise<ServedUserKeys> {
    const certificate = keyring.userId === bobId ? bobCertificate : senderCertificate
    const subkeys = await Promise.all(
      ekIds.map(async (ekId) => {
        // La liaison servie ne porte pas son numéro de série : c'est celui du certificat (PROTOCOL.md 4.4).
        const binding = await bindSubkey(keyring, certificate.serial, ekId)
        return {
          ekId: binding.ekId,
          kem: binding.kem,
          ekPublic: binding.ekPublic,
          createdAt: binding.createdAt,
          expiresAt: binding.expiresAt,
          signature: binding.signature,
        }
      }),
    )
    return { certificate, subkeys }
  }

  return {
    senderId,
    bobId,
    senderCertificate,
    authorityPublicKey: base64url(ca, 'publicKey'),
    receivedAt,
    senderKeyring,
    bobKeyring,
    servedKeys,
    signedByCa,
    async chosen(store, now = sentAt) {
      return {
        own: await chooseSubkey(store, await servedKeys(await senderKeyring()), senderId, now),
        bob: await chooseSubkey(store, await servedKeys(await bobKeyring()), bobId, now),
      }
    },
  }
}
