import { describe, expect, it } from 'vitest'
import { toBase64Url } from './base64url'
import { generateKeyring, type Keyring } from './keyring'
import { compareOwnKeys, neverPublished } from './ownKeys'
import { generateRecoveryKey } from './recovery'

const USER_ID = '0192f7a1-ffff-7000-8000-00000000a11c'
const NOW = 1_794_009_600_000

async function freshKeyring(): Promise<Keyring> {
  return (await generateKeyring(USER_ID, generateRecoveryKey(), NOW)).keyring
}

function servedFor(keyring: Keyring, subkeyIds: readonly Uint8Array[]) {
  return {
    certificate: { ikPublic: toBase64Url(keyring.identityPublicKey), notBefore: NOW },
    subkeys: subkeyIds.map((ekId) => ({ ekId: toBase64Url(ekId) })),
  }
}

describe('KEY-33 : clés servies comparées au trousseau de l’appareil (ADR 0029, point 3)', () => {
  it('KEY-33 : même IK et sous-clés servies toutes dans le trousseau : match, publiée ou non', async () => {
    const keyring = await freshKeyring()
    const ekIds = keyring.subkeys.map((subkey) => subkey.ekId)

    expect(compareOwnKeys(keyring, servedFor(keyring, ekIds))).toEqual({
      kind: 'match',
      published: true,
    })
    expect(compareOwnKeys(keyring, servedFor(keyring, []))).toEqual({
      kind: 'match',
      published: false,
    })
  })

  it('KEY-33, 16.1 : un certificat d’une autre IK, ou illisible, est d’une autre identité', async () => {
    const keyring = await freshKeyring()
    const other = await freshKeyring()

    expect(compareOwnKeys(keyring, servedFor(other, []))).toEqual({ kind: 'foreign' })
    expect(
      compareOwnKeys(keyring, {
        certificate: { ikPublic: `${toBase64Url(keyring.identityPublicKey)}=`, notBefore: NOW },
        subkeys: [],
      }),
    ).toEqual({ kind: 'foreign' })
  })

  it('PROTOCOL.md 5.5 : une sous-clé servie absente du trousseau, ou illisible, le rend périmé', async () => {
    const keyring = await freshKeyring()
    const ekIds = keyring.subkeys.map((subkey) => subkey.ekId)

    expect(compareOwnKeys(keyring, servedFor(keyring, [...ekIds, new Uint8Array(16)]))).toEqual({
      kind: 'stale',
    })
    expect(
      compareOwnKeys(keyring, {
        certificate: { ikPublic: toBase64Url(keyring.identityPublicKey), notBefore: NOW },
        subkeys: [{ ekId: '*' }],
      }),
    ).toEqual({ kind: 'stale' })
  })
})

describe('ADR 0029, point 11 : sous-clé jamais publiée, qu’une neuve remplace dans le coffre', () => {
  const MINUTE_MS = 60_000
  const DAY_MS = 86_400_000
  const ekId = Uint8Array.from({ length: 16 }, (_, index) => index)
  const later = NOW + 6 * MINUTE_MS

  it('ADR 0029, point 11 : non servie, créée après l’émission du certificat et récente : jamais publiée', async () => {
    const keyring = await freshKeyring()

    expect(neverPublished(servedFor(keyring, []), later)({ ekId, createdAt: later })).toBe(true)
  })

  it('ADR 0029, point 11 : servie, créée avant l’émission du certificat (5 minutes de tolérance) ou ancienne : gardée', async () => {
    const keyring = await freshKeyring()
    const replaceable = neverPublished(servedFor(keyring, []), later + 31 * DAY_MS)

    expect(neverPublished(servedFor(keyring, [ekId]), later)({ ekId, createdAt: later })).toBe(
      false,
    )
    expect(
      neverPublished(servedFor(keyring, []), later)({ ekId, createdAt: NOW + 4 * MINUTE_MS }),
    ).toBe(false)
    expect(replaceable({ ekId, createdAt: later })).toBe(false)
    expect(replaceable({ ekId, createdAt: later + 2 * DAY_MS })).toBe(true)
  })
})
