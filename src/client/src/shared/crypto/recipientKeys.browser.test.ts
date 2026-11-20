import { afterEach, describe, expect, it } from 'vitest'
import { commands } from 'vitest/browser'
import { fromBase64Url, toBase64Url } from './base64url'
import type { ServedCertificate } from './certificate'
import { type Keyring, openVault } from './keyring'
import { type KeyStore, openKeyStore, pinAuthority } from './keystore'
import { bindSubkey } from './publication'
import {
  chooseSubkey,
  type ServedSubkey,
  type ServedUserKeys,
  SubkeyUnavailableError,
  type SubkeyUnavailableReason,
} from './recipientKeys'
import { vaultPayload, vectorRecoveryKey } from './testing/vaultVectorSuite'
import { base64url, decimal, number, record, records, string } from './testing/vectorReader'

// QA-02 : certificat de identity.json ; sous-clés du coffre version 2 de vault.json (même IK), liées à ce certificat.
const identity = record(JSON.parse(await commands.readFile('../protocol/vectors/identity.json')))
const vault = record(JSON.parse(await commands.readFile('../protocol/vectors/vault.json')))
const CA_PUBLIC = base64url(record(record(identity['keys'])['ca']), 'publicKey')
const OTHER_PUBLIC = base64url(record(record(identity['keys'])['other']), 'publicKey')
const DAY_MS = 86_400_000

const vector = records(identity['certificates'])[0] ?? {}
const fields = record(vector['fields'])
const CERTIFICATE: ServedCertificate = {
  serial: string(fields, 'serial'),
  issuerKeyId: string(fields, 'issuerKeyId'),
  subjectId: string(fields, 'subjectId'),
  username: string(fields, 'username'),
  ikPublic: string(fields, 'ikPublic'),
  notBefore: decimal(fields, 'notBefore'),
  notAfter: decimal(fields, 'notAfter'),
  usage: number(fields, 'usage'),
  signature: string(vector, 'signature'),
}
const RECIPIENT = CERTIFICATE.subjectId
const NOT_BEFORE = CERTIFICATE.notBefore

const opened: { store: KeyStore; name: string }[] = []

async function pinnedStore(pin: Uint8Array = CA_PUBLIC): Promise<KeyStore> {
  const name = `seal-test-${crypto.randomUUID()}`
  const store = await openKeyStore(name)
  opened.push({ store, name })
  await pinAuthority(store, pin)
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

/** Les deux sous-clés du coffre version 2 : A0…AF créée à notBefore, B0…BF trente jours plus tard. */
async function keyring(): Promise<Keyring> {
  return openVault(
    RECIPIENT,
    vaultPayload(record(vault['nextVersion'])['payload']),
    vectorRecoveryKey(vault),
  )
}

/** Sous-clé servie par GET /users/{id}/keys : la liaison sans son numéro de série, porté par le certificat. */
async function served(
  ring: Keyring,
  index: number,
  serial = CERTIFICATE.serial,
): Promise<ServedSubkey> {
  const subkey = ring.subkeys[index]
  if (subkey === undefined) throw new Error('sous-clé du vecteur attendue')
  const binding = await bindSubkey(ring, serial, subkey.ekId)
  return {
    ekId: binding.ekId,
    kem: binding.kem,
    ekPublic: binding.ekPublic,
    createdAt: binding.createdAt,
    expiresAt: binding.expiresAt,
    signature: binding.signature,
  }
}

async function servedKeys(subkeys: readonly ServedSubkey[]): Promise<ServedUserKeys> {
  return Promise.resolve({ certificate: CERTIFICATE, subkeys })
}

async function refusal(pending: Promise<unknown>): Promise<SubkeyUnavailableReason | undefined> {
  return pending.then(
    () => undefined,
    (error: unknown) => (error instanceof SubkeyUnavailableError ? error.reason : undefined),
  )
}

describe('KEY-20 : sous-clé d’un destinataire, vérifiée avant usage (ADR 0030, point 5)', () => {
  it('KEY-20 : la sous-clé non expirée la plus récente est choisie, quel que soit l’ordre servi', async () => {
    const ring = await keyring()
    const [older, newer] = [await served(ring, 0), await served(ring, 1)]
    const store = await pinnedStore()

    for (const order of [
      [older, newer],
      [newer, older],
    ]) {
      const chosen = await chooseSubkey(
        store,
        await servedKeys(order),
        RECIPIENT,
        NOT_BEFORE + 31 * DAY_MS,
      )

      expect(chosen.recipientId).toBe(RECIPIENT)
      expect(toBase64Url(chosen.ekId)).toBe(newer.ekId)
      expect(toBase64Url(chosen.ekPublic)).toBe(newer.ekPublic)
      expect(toBase64Url(chosen.certSerial)).toBe(CERTIFICATE.serial)
      expect(toBase64Url(chosen.ikPublic)).toBe(CERTIFICATE.ikPublic)
    }
  })

  it('KEY-20 : une sous-clé expirée est écartée ; sans sous-clé valide, la clé est indisponible', async () => {
    const ring = await keyring()
    const subkeys = [await served(ring, 0), await served(ring, 1)]
    const store = await pinnedStore()
    const older = subkeys[0]

    const chosen = await chooseSubkey(
      store,
      await servedKeys(older === undefined ? [] : [older]),
      RECIPIENT,
      NOT_BEFORE + 44 * DAY_MS,
    )
    expect(toBase64Url(chosen.ekId)).toBe(older?.ekId)
    expect(
      await refusal(
        chooseSubkey(
          store,
          await servedKeys(older === undefined ? [] : [older]),
          RECIPIENT,
          NOT_BEFORE + 45 * DAY_MS,
        ),
      ),
    ).toBe('none')
    expect(
      await refusal(
        chooseSubkey(store, await servedKeys(subkeys), RECIPIENT, NOT_BEFORE + 76 * DAY_MS),
      ),
    ).toBe('none')
    expect(
      await refusal(chooseSubkey(store, await servedKeys([]), RECIPIENT, NOT_BEFORE + DAY_MS)),
    ).toBe('none')
  })

  it('ADR 0030, point 5 : une seule liaison invalide refuse toutes les clés du destinataire', async () => {
    const ring = await keyring()
    const [older, newer] = [await served(ring, 0), await served(ring, 1)]
    const store = await pinnedStore()
    const now = NOT_BEFORE + 31 * DAY_MS
    const flipped = Uint8Array.from(fromBase64Url(older.signature))
    flipped[0] = (flipped[0] ?? 0) ^ 0x01
    const otherSerial = toBase64Url(new Uint8Array(16))

    for (const [name, forged] of [
      ['signature altérée', { ...older, signature: toBase64Url(flipped) }],
      ['liée à un autre certificat', await served(ring, 0, otherSerial)],
      ['autre clé publique', { ...older, ekPublic: newer.ekPublic }],
      ['autre KEM', { ...older, kem: 'X-WING' }],
      ['ekId de 15 octets', { ...older, ekId: older.ekId.slice(0, 20) }],
    ] as const) {
      expect(
        await refusal(chooseSubkey(store, await servedKeys([forged, newer]), RECIPIENT, now)),
        name,
      ).toBe('binding')
    }
  })

  it('KEY-20 : un certificat d’un autre sujet, signé par une autre Autorité ou expiré refuse le destinataire', async () => {
    const ring = await keyring()
    const subkeys = [await served(ring, 1)]
    const now = NOT_BEFORE + 31 * DAY_MS

    expect(
      await refusal(
        chooseSubkey(
          await pinnedStore(),
          await servedKeys(subkeys),
          '0192f7a1-ffff-7000-8000-000000000b0b',
          now,
        ),
      ),
    ).toBe('certificate')
    expect(
      await refusal(
        chooseSubkey(await pinnedStore(OTHER_PUBLIC), await servedKeys(subkeys), RECIPIENT, now),
      ),
    ).toBe('certificate')
    expect(
      await refusal(
        chooseSubkey(
          await pinnedStore(),
          await servedKeys(subkeys),
          RECIPIENT,
          CERTIFICATE.notAfter,
        ),
      ),
    ).toBe('certificate')
  })
})
