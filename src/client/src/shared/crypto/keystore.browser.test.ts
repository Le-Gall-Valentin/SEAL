import { afterEach, describe, expect, it, vi } from 'vitest'
import type { CertificateFields } from './identity'
import { createKeyring, generateKeyring, keyringSecrets } from './keyring'
import { type KeyStore, openKeyStore, pinAuthority, saveCertificate } from './keystore'

const USER_ID = '0192f7a1-ffff-7000-8000-00000000a11c'
const OTHER_ID = '0192f7a1-ffff-7000-8000-000000000b0b'

const opened: { store: KeyStore; name: string }[] = []

/** Une base par test, effacée après : aucun test ne voit l'état d'un autre. */
async function freshStore(name = `seal-test-${crypto.randomUUID()}`): Promise<KeyStore> {
  const store = await openKeyStore(name)
  opened.push({ store, name })
  return store
}

afterEach(async () => {
  vi.restoreAllMocks()
  // Toutes les connexions se ferment d'abord : une base encore ouverte bloquerait son effacement.
  const stores = opened.splice(0)
  for (const { store } of stores) {
    store.close()
  }
  for (const name of new Set(stores.map((entry) => entry.name))) {
    await new Promise((resolve) => {
      indexedDB.deleteDatabase(name).onsuccess = resolve
    })
  }
})

describe('KEY-40 : trousseau dans IndexedDB', () => {
  it('KEY-40 : un trousseau relu de la base signe toujours, et ses clés ne s’exportent pas', async () => {
    const { keyring } = await generateKeyring(USER_ID, Date.now())
    const store = await freshStore()
    await store.saveKeyring(keyring)

    const reloaded = await store.loadKeyring(USER_ID)
    if (reloaded === undefined) throw new Error('trousseau attendu')
    const message = new TextEncoder().encode('SEAL')
    const secrets = keyringSecrets(reloaded)
    const signature = await crypto.subtle.sign('Ed25519', secrets.identityKey, message)
    const publicKey = await crypto.subtle.importKey(
      'raw',
      reloaded.identityPublicKey,
      'Ed25519',
      false,
      ['verify'],
    )

    await expect(crypto.subtle.verify('Ed25519', publicKey, signature, message)).resolves.toBe(true)
    expect(reloaded.subkeys).toEqual(keyring.subkeys)
    expect(secrets.subkeyKeys).toHaveLength(1)
    for (const key of [secrets.identityKey, ...secrets.subkeyKeys, secrets.vdk]) {
      expect(key.extractable).toBe(false)
      await expect(crypto.subtle.exportKey('jwk', key)).rejects.toThrow()
    }
  })

  it('ADR 0027, point 12 : un trousseau dont une clé est extractable n’est pas enregistré', async () => {
    const { keyring } = await generateKeyring(USER_ID, Date.now())
    const secrets = keyringSecrets(keyring)
    const extractable = await crypto.subtle.generateKey({ name: 'Ed25519' }, true, [
      'sign',
      'verify',
    ])
    const leaky = createKeyring(keyring, { ...secrets, identityKey: extractable.privateKey })
    const store = await freshStore()

    await expect(store.saveKeyring(leaky)).rejects.toThrow(TypeError)
    await expect(store.loadKeyring(USER_ID)).resolves.toBeUndefined()
  })

  it('KEY-40 : le trousseau d’un compte ne se lit pas sous un autre identifiant', async () => {
    const { keyring } = await generateKeyring(USER_ID, Date.now())
    const store = await freshStore()
    await store.saveKeyring(keyring)

    await expect(store.loadKeyring(OTHER_ID)).resolves.toBeUndefined()
    await expect(store.loadCertificate(USER_ID)).resolves.toBeUndefined()
  })

  it('KEY-40 : le trousseau survit à la fermeture de la base, comme à un rechargement de la page', async () => {
    const name = `seal-test-${crypto.randomUUID()}`
    const { keyring } = await generateKeyring(USER_ID, Date.now())
    const first = await freshStore(name)
    await first.saveKeyring(keyring)
    first.close()

    const second = await freshStore(name)

    expect((await second.loadKeyring(USER_ID))?.identityPublicKey).toEqual(
      keyring.identityPublicKey,
    )
  })

  it('ADR 0012 (Q6) : l’enregistrement demande que la base ne soit pas évincée', async () => {
    const persist = vi.spyOn(navigator.storage, 'persist').mockResolvedValue(true)
    const { keyring } = await generateKeyring(USER_ID, Date.now())
    const store = await freshStore()

    await store.saveKeyring(keyring)

    expect(persist).toHaveBeenCalled()
  })

  it('ADR 0012 (Q6) : un refus de persistance n’empêche pas l’enregistrement', async () => {
    vi.spyOn(navigator.storage, 'persist').mockRejectedValue(
      new DOMException('refus', 'NotAllowedError'),
    )
    const { keyring } = await generateKeyring(USER_ID, Date.now())
    const store = await freshStore()

    await store.saveKeyring(keyring)

    expect(await store.loadKeyring(USER_ID)).toBeDefined()
  })
})

describe('KEY-11 : clé de l’Autorité épinglée', () => {
  const first = new Uint8Array(32).fill(1)
  const second = new Uint8Array(32).fill(2)

  it('KEY-11 : la première clé est épinglée, et une autre ne la remplace jamais', async () => {
    const store = await freshStore()

    await expect(store.loadAuthority()).resolves.toBeUndefined()
    await expect(pinAuthority(store, first)).resolves.toEqual(first)
    await expect(pinAuthority(store, second)).resolves.toEqual(first)
    await expect(store.loadAuthority()).resolves.toEqual(first)
  })

  it('KEY-11 : deux onglets qui épinglent en même temps gardent une seule clé', async () => {
    const name = `seal-test-${crypto.randomUUID()}`
    const left = await freshStore(name)
    const right = await freshStore(name)

    const [fromLeft, fromRight] = await Promise.all([
      pinAuthority(left, first),
      pinAuthority(right, second),
    ])

    expect(fromLeft).toEqual(fromRight)
    await expect(left.loadAuthority()).resolves.toEqual(fromLeft)
  })

  it('KEY-11 : la clé rendue est une copie : la modifier ne change pas l’épinglage', async () => {
    const store = await freshStore()
    const pinned = await pinAuthority(store, first)
    pinned.fill(9)

    await expect(store.loadAuthority()).resolves.toEqual(first)
  })
})

describe('KEY-06 : certificat enregistré', () => {
  const certificate: CertificateFields & { readonly signature: Uint8Array } = {
    serial: new Uint8Array(16).fill(3),
    issuerKeyId: new Uint8Array(8).fill(4),
    subjectId: USER_ID,
    username: 'valentin.dupont',
    ikPublic: new Uint8Array(32).fill(5),
    notBefore: 1,
    notAfter: 2,
    usage: 3,
    signature: new Uint8Array(64).fill(6),
  }

  it('KEY-06 : un certificat enregistré se relit à l’identique, pour son seul compte', async () => {
    const store = await freshStore()

    await saveCertificate(store, USER_ID, certificate)

    await expect(store.loadCertificate(USER_ID)).resolves.toEqual(certificate)
    await expect(store.loadCertificate(OTHER_ID)).resolves.toBeUndefined()
  })

  it('KEY-06 : seul un trousseau ouvert par openKeyStore accepte un certificat', async () => {
    const forged: KeyStore = {
      loadKeyring: () => Promise.resolve(undefined),
      saveKeyring: () => Promise.resolve(),
      loadCertificate: () => Promise.resolve(undefined),
      loadAuthority: () => Promise.resolve(undefined),
      close: () => undefined,
    }

    await expect(saveCertificate(forged, USER_ID, certificate)).rejects.toThrow(TypeError)
  })
})
