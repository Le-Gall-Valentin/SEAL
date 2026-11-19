import { describe, expect, it } from 'vitest'
import { toBase64Url } from './base64url'
import { generateKeyring, type Keyring } from './keyring'
import { compareOwnKeys } from './ownKeys'
import { generateRecoveryKey } from './recovery'

const USER_ID = '0192f7a1-ffff-7000-8000-00000000a11c'
const NOW = 1_794_009_600_000

async function freshKeyring(): Promise<Keyring> {
  return (await generateKeyring(USER_ID, generateRecoveryKey(), NOW)).keyring
}

function servedFor(keyring: Keyring, subkeyIds: readonly Uint8Array[]) {
  return {
    certificate: { ikPublic: toBase64Url(keyring.identityPublicKey) },
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
        certificate: { ikPublic: `${toBase64Url(keyring.identityPublicKey)}=` },
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
        certificate: { ikPublic: toBase64Url(keyring.identityPublicKey) },
        subkeys: [{ ekId: '*' }],
      }),
    ).toEqual({ kind: 'stale' })
  })
})
