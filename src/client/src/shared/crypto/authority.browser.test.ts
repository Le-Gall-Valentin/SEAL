import { afterEach, describe, expect, it } from 'vitest'
import { commands } from 'vitest/browser'
import { checkAuthority, type ServedAuthority } from './authority'
import { toBase64Url } from './base64url'
import { type KeyStore, openKeyStore } from './keystore'
import { base64url, record, string } from './testing/vectorReader'

// Chemin relatif à la racine du client (src/client), comme le résout commands.readFile.
const identity = record(JSON.parse(await commands.readFile('../protocol/vectors/identity.json')))
const AUTHORITY = record(identity['authority'])
const SERVED: ServedAuthority = {
  publicKey: string(AUTHORITY, 'publicKey'),
  keyId: string(AUTHORITY, 'keyId'),
  fingerprint: string(AUTHORITY, 'fingerprint'),
}

/** Vue servie pour une clé quelconque, keyId et empreinte calculés comme le serveur (PROTOCOL.md 4.1). */
async function servedFor(publicKey: Uint8Array<ArrayBuffer>): Promise<ServedAuthority> {
  const digest = new Uint8Array(await crypto.subtle.digest('SHA-256', publicKey))
  return {
    publicKey: toBase64Url(publicKey),
    keyId: toBase64Url(digest.subarray(0, 8)),
    fingerprint: Array.from(digest, (byte) => byte.toString(16).padStart(2, '0'))
      .join('')
      .toUpperCase(),
  }
}

const names: string[] = []
const stores: KeyStore[] = []

async function freshStore(name = `seal-test-${crypto.randomUUID()}`): Promise<KeyStore> {
  const store = await openKeyStore(name)
  names.push(name)
  stores.push(store)
  return store
}

afterEach(async () => {
  for (const store of stores.splice(0)) store.close()
  for (const name of new Set(names.splice(0))) {
    await new Promise((resolve) => {
      indexedDB.deleteDatabase(name).onsuccess = resolve
    })
  }
})

describe('KEY-11 : Autorité épinglée au premier usage', () => {
  it('KEY-11 : la clé servie est épinglée au premier usage, puis reconnue', async () => {
    const store = await freshStore()

    await expect(checkAuthority(store, SERVED)).resolves.toBe('pinned')
    await expect(checkAuthority(store, SERVED)).resolves.toBe('unchanged')
    await expect(store.loadAuthority()).resolves.toEqual(base64url(AUTHORITY, 'publicKey'))
  })

  it('KEY-11 : une autre clé servie ensuite est un changement, encore après réouverture de la base', async () => {
    const name = `seal-test-${crypto.randomUUID()}`
    const other = await servedFor(base64url(record(record(identity['keys'])['other']), 'publicKey'))
    const store = await freshStore(name)
    await checkAuthority(store, SERVED)

    await expect(checkAuthority(store, other)).resolves.toBe('changed')
    store.close()
    await expect(checkAuthority(await freshStore(name), other)).resolves.toBe('changed')
  })

  it('KEY-11 : si un autre onglet épingle une autre clé juste avant, c’est un changement', async () => {
    const store = await freshStore()
    const other = await servedFor(base64url(record(record(identity['keys'])['other']), 'publicKey'))
    await checkAuthority(store, other)
    // Lecture faite avant l'épinglage de l'autre onglet : le trousseau semble vide, l'écriture trouve la clé posée.
    store.loadAuthority = () => Promise.resolve(undefined)

    await expect(checkAuthority(store, SERVED)).resolves.toBe('changed')
  })

  it('ADR 0027, point 5 : une réponse incohérente est invalide et n’est jamais épinglée', async () => {
    const store = await freshStore()
    const smallOrder = record(identity['smallOrder'])['points']
    if (!Array.isArray(smallOrder) || typeof smallOrder[0] !== 'string')
      throw new Error('points attendus')
    const weak = await servedFor(base64url({ value: smallOrder[0] }, 'value'))
    const cases: ServedAuthority[] = [
      { ...SERVED, keyId: (await servedFor(new Uint8Array(32).fill(7))).keyId },
      { ...SERVED, fingerprint: SERVED.fingerprint.toLowerCase() },
      { ...SERVED, fingerprint: (await servedFor(new Uint8Array(32).fill(7))).fingerprint },
      { ...SERVED, publicKey: SERVED.publicKey.slice(1) },
      { ...SERVED, publicKey: `${SERVED.publicKey}=` },
      weak,
    ]

    for (const served of cases) {
      await expect(checkAuthority(store, served)).resolves.toBe('invalid')
    }
    await expect(store.loadAuthority()).resolves.toBeUndefined()
    await expect(checkAuthority(store, SERVED)).resolves.toBe('pinned')
  })

  it('ADR 0027, point 5 : une réponse incohérente reste invalide après l’épinglage', async () => {
    const store = await freshStore()
    await checkAuthority(store, SERVED)

    await expect(checkAuthority(store, { ...SERVED, fingerprint: '0'.repeat(64) })).resolves.toBe(
      'invalid',
    )
    await expect(store.loadAuthority()).resolves.toEqual(base64url(AUTHORITY, 'publicKey'))
  })
})
