import { describe, expect, it } from 'vitest'
import { generateKeyring, generateRecoveryKey, type KeyStore } from '@/shared/crypto'
import { withLock } from '@/shared/lib'
import {
  completeKeys,
  ForeignIdentityError,
  type KeysContext,
  StaleVaultError,
} from './completeKeys'
import { keyStatus } from './keyStatus'
import { fakeKeysServer, type FakeKeysServer } from './testing/fakeKeysServer'
import {
  pinnedStore,
  VECTOR_CERTIFICATE,
  VECTOR_NEXT_VAULT,
  VECTOR_OWNER,
  VECTOR_SUBKEY_CREATED_AT,
  VECTOR_VAULT,
  vectorKeyring,
} from './testing/keyVectors'

const MINUTE_MS = 60_000
const DAY_MS = 86_400_000
/** La sous-clé des vecteurs a une minute : elle se publie telle quelle. */
const FRESH = VECTOR_SUBKEY_CREATED_AT + MINUTE_MS

interface Scenario {
  readonly server: FakeKeysServer
  readonly store: KeyStore
  readonly context: KeysContext
}

/**
 * Trousseau des vecteurs enregistré sur l'appareil, coffre version 1 sur le serveur, rien de publié. Le serveur et
 * l'appareil lisent la même horloge : `now`, ou `clock` pour la faire avancer.
 */
async function scenario(now = FRESH, clock: () => number = () => now): Promise<Scenario> {
  const server = fakeKeysServer(VECTOR_CERTIFICATE, clock)
  server.vault = VECTOR_VAULT
  const store = await pinnedStore()
  await store.saveKeyring(await vectorKeyring())
  return {
    server,
    store,
    context: { api: server, store, owner: VECTOR_OWNER, clock: { now: clock } },
  }
}

async function failed(pending: Promise<unknown>): Promise<unknown> {
  return pending.then(
    () => undefined,
    (error: unknown) => error,
  )
}

describe('KEY-02, KEY-05, KEY-06 : achèvement du trousseau (ADR 0029, point 3)', () => {
  it('KEY-02 : sans certificat, publie l’identité puis la sous-clé, et enregistre le certificat en dernier', async () => {
    const { server, store, context } = await scenario()

    await completeKeys(context)

    expect(server.calls).toEqual([
      'readOwnKeys',
      'createChallenge',
      'publishIdentity',
      'publishSubkey',
    ])
    expect(server.subkeys).toHaveLength(1)
    await expect(store.loadCertificate(VECTOR_OWNER.userId)).resolves.toMatchObject({
      username: VECTOR_OWNER.username,
    })
  })

  it('KEY-32 : une sous-clé de plus de 4 minutes passe par une version suivante du coffre avant sa publication', async () => {
    const { server, store, context } = await scenario(VECTOR_SUBKEY_CREATED_AT + DAY_MS)

    await completeKeys(context)

    expect(server.calls).toEqual([
      'readOwnKeys',
      'createChallenge',
      'publishIdentity',
      'readVault',
      'saveVault',
      'publishSubkey',
    ])
    expect(server.vault?.vaultVersion).toBe(2)
    const local = await store.loadKeyring(VECTOR_OWNER.userId)
    expect(local?.subkeys).toHaveLength(2)
    expect(server.subkeys[0]?.createdAt).toBe(VECTOR_SUBKEY_CREATED_AT + DAY_MS)
  })

  it('ADR 0029 : arrêt après chaque étape, puis reprise, sans second certificat ni seconde sous-clé', async () => {
    for (const [method, when] of [
      ['publishIdentity', 'after'],
      ['publishSubkey', 'before'],
      ['publishSubkey', 'after'],
      ['readOwnKeys', 'before'],
    ] as const) {
      const { server, store, context } = await scenario()
      server.fail(method, when)

      expect(await failed(completeKeys(context)), `${method} ${when}`).toBeInstanceOf(Error)
      await expect(store.loadCertificate(VECTOR_OWNER.userId)).resolves.toBeUndefined()

      await completeKeys(context)
      expect(server.calls.filter((call) => call === 'publishIdentity').length).toBeLessThanOrEqual(
        1,
      )
      expect(server.subkeys, `${method} ${when}`).toHaveLength(1)
      await expect(store.loadCertificate(VECTOR_OWNER.userId)).resolves.toBeDefined()
    }
  })

  it('KEY-12 : un 403 à la publication de l’identité fait relire le certificat publié entre-temps', async () => {
    const { server, context } = await scenario()
    server.certificate = VECTOR_CERTIFICATE
    server.hideCertificateOnce()

    await completeKeys(context)

    expect(server.calls.slice(0, 4)).toEqual([
      'readOwnKeys',
      'createChallenge',
      'publishIdentity',
      'readOwnKeys',
    ])
    expect(server.subkeys).toHaveLength(1)
  })

  it('16.1 : un certificat d’une autre clé d’identité que celle de l’appareil bloque, sans rien publier', async () => {
    const { server, store, context } = await scenario()
    const { keyring: other } = await generateKeyring(
      VECTOR_OWNER.userId,
      generateRecoveryKey(),
      FRESH,
    )
    await store.saveKeyring(other)
    server.certificate = VECTOR_CERTIFICATE

    expect(await failed(completeKeys(context))).toBeInstanceOf(ForeignIdentityError)
    expect(server.calls).toEqual(['readOwnKeys'])
    await expect(store.loadCertificate(VECTOR_OWNER.userId)).resolves.toBeUndefined()
  })

  it('PROTOCOL.md 5.5 : une sous-clé publiée ailleurs se relit dans le coffre par la VDK ; absente, la reprise est refusée', async () => {
    // La seconde sous-clé du coffre version 2 (ekId B0…BF), publiée par un autre appareil.
    const elsewhere = {
      ekId: 'sLGys7S1tre4ubq7vL2-vw',
      kem: 'DHKEM-X25519-HKDF-SHA256' as const,
      ekPublic: 'A'.repeat(43),
      createdAt: FRESH,
      expiresAt: FRESH + 45 * DAY_MS,
      signature: 'A'.repeat(86),
    }
    const { server, store, context } = await scenario()
    server.vault = VECTOR_NEXT_VAULT
    server.certificate = VECTOR_CERTIFICATE
    server.subkeys = [elsewhere]

    await completeKeys(context)

    expect((await store.loadKeyring(VECTOR_OWNER.userId))?.subkeys).toHaveLength(2)
    expect(server.calls).toEqual(['readOwnKeys', 'readVault'])
    await expect(store.loadCertificate(VECTOR_OWNER.userId)).resolves.toBeDefined()

    const replayed = await scenario()
    replayed.server.certificate = VECTOR_CERTIFICATE
    replayed.server.subkeys = [elsewhere]
    expect(await failed(completeKeys(replayed.context))).toBeInstanceOf(StaleVaultError)
    await expect(replayed.store.loadCertificate(VECTOR_OWNER.userId)).resolves.toBeUndefined()
  })

  it('ADR 0029, point 11 : des publications refusées à chaque reprise ne font pas grossir le coffre', async () => {
    let now = VECTOR_SUBKEY_CREATED_AT + DAY_MS
    const { server, store, context: clocked } = await scenario(now, () => now)

    for (let attempt = 0; attempt < 6; attempt += 1) {
      now += 5 * MINUTE_MS
      server.fail('publishSubkey', 'before')
      expect(await failed(completeKeys(clocked))).toBeInstanceOf(Error)
    }

    expect(server.vault?.vaultVersion).toBe(7)
    expect((await store.loadKeyring(VECTOR_OWNER.userId))?.subkeys).toHaveLength(2)
    now += 5 * MINUTE_MS
    await completeKeys(clocked)
    expect(server.subkeys).toHaveLength(1)
    expect((await store.loadKeyring(VECTOR_OWNER.userId))?.subkeys).toHaveLength(2)
  })

  it('ADR 0029, point 11 : un autre appareil, qui garde la sous-clé avant qu’elle soit redatée, reprend sans refus', async () => {
    let now = VECTOR_SUBKEY_CREATED_AT + DAY_MS
    const first = await scenario(now, () => now)
    const clocked = first.context
    first.server.fail('publishSubkey', 'before')
    expect(await failed(completeKeys(clocked))).toBeInstanceOf(Error)
    const restored = await first.store.loadKeyring(VECTOR_OWNER.userId)
    if (restored === undefined) throw new Error('trousseau attendu')
    const second = await pinnedStore()
    await second.saveKeyring(restored)

    now += 5 * MINUTE_MS
    await completeKeys(clocked)
    now += MINUTE_MS
    await completeKeys({ ...clocked, store: second })

    const published = first.server.subkeys.map((subkey) => subkey.ekId)
    expect(published).toHaveLength(1)
    expect((await second.loadKeyring(VECTOR_OWNER.userId))?.subkeys).toHaveLength(2)
    await expect(second.loadCertificate(VECTOR_OWNER.userId)).resolves.toBeDefined()
  })

  it('ADR 0029, point 11 : une sous-clé publiée que le serveur tait est redatée, sa republication refusée, et rien n’est retiré', async () => {
    let now = VECTOR_SUBKEY_CREATED_AT + DAY_MS
    const { server, store, context } = await scenario(now, () => now)
    await completeKeys(context)
    const before = (await store.loadKeyring(VECTOR_OWNER.userId))?.subkeys.map((s) => s.ekId)
    expect(before).toHaveLength(2)

    server.subkeys = []
    now += 10 * MINUTE_MS
    expect(await failed(completeKeys(context))).toMatchObject({ code: 'VALIDATION_FAILED' })

    expect(server.vault?.vaultVersion).toBe(3)
    const after = (await store.loadKeyring(VECTOR_OWNER.userId))?.subkeys.map((s) => s.ekId)
    expect(after).toEqual(before)
  })

  it('ADR 0029 : deux onglets qui reprennent ensemble, sous le verrou, ne publient qu’une fois', async () => {
    const { server, context } = await scenario()

    await Promise.all([
      withLock('seal-keys-test', () => completeKeys(context)),
      withLock('seal-keys-test', () => completeKeys(context)),
    ])

    expect(server.calls.filter((call) => call === 'publishIdentity')).toHaveLength(1)
    expect(server.subkeys).toHaveLength(1)
  })
})

describe('KEY-02, KEY-33 : statut du trousseau à l’entrée dans la zone du compte', () => {
  it('KEY-02 : ni trousseau ni coffre : première connexion', async () => {
    const server = fakeKeysServer(VECTOR_CERTIFICATE, () => FRESH)
    const store = await pinnedStore()

    await expect(
      keyStatus({ api: server, store, owner: VECTOR_OWNER, clock: { now: () => FRESH } }),
    ).resolves.toBe('setup')
  })

  it('KEY-33 : pas de trousseau sur l’appareil mais un coffre : restauration', async () => {
    const server = fakeKeysServer(VECTOR_CERTIFICATE, () => FRESH)
    server.vault = VECTOR_VAULT
    const store = await pinnedStore()

    await expect(
      keyStatus({ api: server, store, owner: VECTOR_OWNER, clock: { now: () => FRESH } }),
    ).resolves.toBe('restore')
  })

  it('ADR 0029 : un trousseau sans certificat reprend la publication, puis est prêt sans plus rien demander', async () => {
    const { server, context } = await scenario()

    await expect(keyStatus(context)).resolves.toBe('ready')
    const calls = server.calls.length
    await expect(keyStatus(context)).resolves.toBe('ready')
    expect(server.calls).toHaveLength(calls)
  })

  it('16.1 : un certificat d’une autre clé d’identité donne foreign', async () => {
    const { server, store, context } = await scenario()
    const { keyring: other } = await generateKeyring(
      VECTOR_OWNER.userId,
      generateRecoveryKey(),
      FRESH,
    )
    await store.saveKeyring(other)
    server.certificate = VECTOR_CERTIFICATE

    await expect(keyStatus(context)).resolves.toBe('foreign')
  })
})
