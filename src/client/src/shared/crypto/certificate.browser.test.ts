import { afterEach, describe, expect, it } from 'vitest'
import { commands } from 'vitest/browser'
import { toBase64Url } from './base64url'
import {
  CertificateRejectedError,
  type CertificateRejection,
  type ServedCertificate,
  storeOwnCertificate,
  verifyOwnCertificate,
} from './certificate'
import { encodeCertificate } from './identity'
import { type KeyStore, openKeyStore, pinAuthority, type StoredCertificate } from './keystore'
import { base64url, decimal, number, record, records, string } from './testing/vectorReader'

// QA-02 : vecteurs de vectors/identity.json ; chemin relatif à la racine du client, comme le résout readFile.
const identity = record(JSON.parse(await commands.readFile('../protocol/vectors/identity.json')))
const keys = record(identity['keys'])
const CA_PUBLIC = base64url(record(keys['ca']), 'publicKey')
const CA_SEED = base64url(record(keys['ca']), 'seed')
const DAY_MS = 86_400_000
const SKEW_MS = 5 * 60_000

function certificate(vector: unknown): StoredCertificate {
  const json = record(vector)
  const fields = record(json['fields'])
  return {
    serial: base64url(fields, 'serial'),
    issuerKeyId: base64url(fields, 'issuerKeyId'),
    subjectId: string(fields, 'subjectId'),
    username: string(fields, 'username'),
    ikPublic: base64url(fields, 'ikPublic'),
    notBefore: decimal(fields, 'notBefore'),
    notAfter: decimal(fields, 'notAfter'),
    usage: number(fields, 'usage'),
    signature: base64url(json, 'signature'),
  }
}

/** Le certificat tel que le serveur le sert : octets en base64url, dates en millisecondes (ADR 0029). */
function served(fields: StoredCertificate): ServedCertificate {
  return {
    serial: toBase64Url(fields.serial),
    issuerKeyId: toBase64Url(fields.issuerKeyId),
    subjectId: fields.subjectId,
    username: fields.username,
    ikPublic: toBase64Url(fields.ikPublic),
    notBefore: fields.notBefore,
    notAfter: fields.notAfter,
    usage: fields.usage,
    signature: toBase64Url(fields.signature),
  }
}

const VALID = certificate(records(identity['certificates'])[0])
const OWNER = { userId: VALID.subjectId, username: VALID.username, ikPublic: VALID.ikPublic }
const NOW = VALID.notBefore + DAY_MS

/** Signe des champs comme la CA des vecteurs : seule sa graine publique de la RFC 8032 le permet. */
async function signedByCa(
  fields: Omit<StoredCertificate, 'signature'>,
): Promise<StoredCertificate> {
  const prefix = Uint8Array.of(
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
  const pkcs8 = new Uint8Array(48)
  pkcs8.set(prefix)
  pkcs8.set(CA_SEED, 16)
  const key = await crypto.subtle.importKey('pkcs8', pkcs8, 'Ed25519', false, ['sign'])
  const signature = new Uint8Array(
    await crypto.subtle.sign('Ed25519', key, encodeCertificate(fields)),
  )
  return { ...fields, signature }
}

const opened: { store: KeyStore; name: string }[] = []

/** Trousseau neuf, avec la CA des vecteurs épinglée, une autre clé, ou rien (null). */
async function pinnedStore(pin: Uint8Array | null = CA_PUBLIC): Promise<KeyStore> {
  const name = `seal-test-${crypto.randomUUID()}`
  const store = await openKeyStore(name)
  opened.push({ store, name })
  if (pin !== null) await pinAuthority(store, pin)
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

async function expectRejected(
  store: KeyStore,
  received: StoredCertificate,
  reason: CertificateRejection,
  owner = OWNER,
  now = NOW,
) {
  const error: unknown = await storeOwnCertificate(store, served(received), owner, now).then(
    () => undefined,
    (thrown: unknown) => thrown,
  )
  expect(error).toBeInstanceOf(CertificateRejectedError)
  expect(error instanceof CertificateRejectedError ? error.reason : undefined).toBe(reason)
  await expect(store.loadCertificate(owner.userId)).resolves.toBeUndefined()
}

describe('KEY-06 : le client vérifie le certificat reçu avant de l’enregistrer', () => {
  it('KEY-06 : les certificats des vecteurs sont acceptés, puis relus à l’identique', async () => {
    for (const vector of records(identity['certificates'])) {
      const received = certificate(vector)
      const owner = {
        userId: received.subjectId,
        username: received.username,
        ikPublic: received.ikPublic,
      }
      const store = await pinnedStore()

      await storeOwnCertificate(store, served(received), owner, NOW)

      await expect(store.loadCertificate(owner.userId)).resolves.toEqual(received)
    }
  })

  it('KEY-06 : le certificat d’un autre compte est refusé', async () => {
    await expectRejected(await pinnedStore(), VALID, 'subject', {
      ...OWNER,
      userId: '0192f7a1-ffff-7000-8000-000000000b0b',
    })
  })

  it('KEY-06 : un autre identifiant est refusé', async () => {
    await expectRejected(await pinnedStore(), VALID, 'username', {
      ...OWNER,
      username: 'anne_marie.tremblay2',
    })
  })

  it('KEY-06 : une autre clé d’identité est refusée', async () => {
    await expectRejected(await pinnedStore(), VALID, 'key', {
      ...OWNER,
      ikPublic: new Uint8Array(32).fill(9),
    })
  })

  it('KEY-06 : un certificat expiré, ou valable dans plus de 5 minutes, est refusé', async () => {
    const store = await pinnedStore()
    await expectRejected(store, VALID, 'validity', OWNER, VALID.notAfter)
    await expectRejected(store, VALID, 'validity', OWNER, VALID.notBefore - SKEW_MS - 1)
  })

  it('ADR 0027, point 9 : une horloge en retard de 5 minutes au plus accepte le certificat tout juste émis', async () => {
    await storeOwnCertificate(await pinnedStore(), served(VALID), OWNER, VALID.notBefore - SKEW_MS)
    await storeOwnCertificate(await pinnedStore(), served(VALID), OWNER, VALID.notAfter - 1)
  })

  it('KEY-06 : seuls les champs vérifiés sont enregistrés, copiés au début de la vérification', async () => {
    let reads = 0
    const received = { ...served(VALID), extra: '<img src=x onerror=alert(1)>' }
    Object.defineProperty(received, 'username', {
      enumerable: true,
      get: () => (++reads === 1 ? VALID.username : 'mallory'),
    })
    const store = await pinnedStore()

    await storeOwnCertificate(store, received, OWNER, NOW)

    await expect(store.loadCertificate(OWNER.userId)).resolves.toEqual(VALID)
  })

  it('ADR 0029 : un base64url non canonique est refusé avant toute vérification', async () => {
    const store = await pinnedStore()
    const padded = { ...served(VALID), signature: `${served(VALID).signature}=` }

    const error: unknown = await storeOwnCertificate(store, padded, OWNER, NOW).then(
      () => undefined,
      (thrown: unknown) => thrown,
    )

    expect(error instanceof CertificateRejectedError ? error.reason : error).toBe('encoding')
    await expect(store.loadCertificate(OWNER.userId)).resolves.toBeUndefined()
  })

  it('KEY-06, ADR 0029 (point 3) : verifyOwnCertificate vérifie de même, sans rien enregistrer', async () => {
    const store = await pinnedStore()

    await verifyOwnCertificate(store, served(VALID), OWNER, NOW)
    await expect(store.loadCertificate(OWNER.userId)).resolves.toBeUndefined()
    await expect(
      verifyOwnCertificate(store, served(VALID), { ...OWNER, username: 'mallory' }, NOW),
    ).rejects.toMatchObject({ reason: 'username' })
  })

  it('KEY-04 : une validité autre que 397 jours est refusée, même signée par l’Autorité', async () => {
    const shorter = await signedByCa({ ...VALID, notAfter: VALID.notAfter - DAY_MS })

    await expectRejected(await pinnedStore(), shorter, 'validity')
  })

  it('KEY-04 : un usage 0b01, signé par l’Autorité, est refusé', async () => {
    const signatureOnly = await signedByCa({ ...VALID, usage: 0b01 })

    await expectRejected(await pinnedStore(), signatureOnly, 'usage')
  })

  it('KEY-04 : un numéro de série de 15 octets est refusé', async () => {
    const shortSerial = await signedByCa({ ...VALID, serial: VALID.serial.slice(1) })

    await expectRejected(await pinnedStore(), shortSerial, 'serial')
  })

  it('PROTOCOL.md 4.1 : un issuerKeyId qui n’est pas celui de la clé épinglée est refusé', async () => {
    const otherIssuer = await signedByCa({ ...VALID, issuerKeyId: new Uint8Array(8).fill(1) })

    await expectRejected(await pinnedStore(), otherIssuer, 'issuer')
  })

  it('KEY-06, QA-04 : les falsifications de certificats des vecteurs sont refusées', async () => {
    const forgeries = records(identity['forgeries']).filter(
      (forgery) => string(forgery, 'structure') === 'certificate',
    )
    expect(forgeries.length).toBeGreaterThan(0)
    for (const forgery of forgeries) {
      await expectRejected(await pinnedStore(), certificate(forgery), 'signature')
    }
  })

  it('KEY-11 : sans Autorité épinglée, aucun certificat n’est accepté', async () => {
    await expectRejected(await pinnedStore(null), VALID, 'authority')
  })

  it('KEY-11 : un certificat signé par l’Autorité des vecteurs est refusé si une autre clé est épinglée', async () => {
    await expectRejected(
      await pinnedStore(base64url(record(keys['other']), 'publicKey')),
      VALID,
      'issuer',
    )
  })

  it('PROTOCOL.md 2.3 : des champs impossibles à encoder sont refusés comme une signature fausse', async () => {
    await expectRejected(await pinnedStore(), { ...VALID, subjectId: 'pas-un-uuid' }, 'signature')
  })
})

describe('KEY-06, KEY-11 : certificats forgés, repris de la revue de sécurité (CDC 16.1)', () => {
  it('KEY-11 : même une Autorité d’ordre faible épinglée n’ouvre pas la porte à un certificat forgé (R neutre, S = 0)', async () => {
    const neutral = Uint8Array.from({ length: 32 }, (_, index) => (index === 0 ? 1 : 0))
    const store = await pinnedStore(null)
    await pinAuthority(store, neutral)
    const digest = new Uint8Array(await crypto.subtle.digest('SHA-256', neutral))
    const forged = {
      ...VALID,
      issuerKeyId: digest.slice(0, 8),
      signature: concatBytes(neutral, new Uint8Array(32)),
    }

    await expectRejected(store, forged, 'signature')
  })

  it('QA-04 : une signature de l’Autorité dont S est augmenté de L est refusée', async () => {
    const L = 2n ** 252n + 27742317777372353535851937790883648493n
    const s = VALID.signature
      .subarray(32)
      .reduceRight((value, byte) => (value << 8n) | BigInt(byte), 0n)
    const bigger = Uint8Array.from({ length: 32 }, (_, index) =>
      Number(((s + L) >> BigInt(8 * index)) & 0xffn),
    )

    await expectRejected(
      await pinnedStore(),
      { ...VALID, signature: concatBytes(VALID.signature.subarray(0, 32), bigger) },
      'signature',
    )
  })

  it('KEY-06 : un subjectId en majuscules, signé par l’Autorité, n’est pas celui du compte', async () => {
    const upper = await signedByCa({ ...VALID, subjectId: VALID.subjectId.toUpperCase() })

    await expectRejected(await pinnedStore(), upper, 'subject')
  })
})

function concatBytes(left: Uint8Array, right: Uint8Array): Uint8Array<ArrayBuffer> {
  const out = new Uint8Array(left.length + right.length)
  out.set(left)
  out.set(right, left.length)
  return out
}
