import { describe, expect, it } from 'vitest'
import { generateRecoveryKey, parseRecoveryKey } from '@/shared/crypto'
import { InvalidVaultError, restoreKeys, WrongRecoveryKeyError } from './restoreKeys'
import { fakeKeysServer } from './testing/fakeKeysServer'
import {
  pinnedStore,
  VECTOR_CERTIFICATE,
  VECTOR_NEXT_VAULT,
  VECTOR_OWNER,
  VECTOR_SUBKEY_CREATED_AT,
  VECTOR_VAULT,
  vectorRecoveryKeyText,
} from './testing/keyVectors'

const NOW = VECTOR_SUBKEY_CREATED_AT + 60_000
const DAY_MS = 86_400_000

/** La seconde sous-clé du coffre version 2 (ekId B0…BF), telle que le serveur la sert. */
const SECOND_SUBKEY = {
  ekId: 'sLGys7S1tre4ubq7vL2-vw',
  kem: 'DHKEM-X25519-HKDF-SHA256' as const,
  ekPublic: 'A'.repeat(43),
  createdAt: NOW,
  expiresAt: NOW + 45 * DAY_MS,
  signature: 'A'.repeat(86),
}

async function scenario() {
  const server = fakeKeysServer(VECTOR_CERTIFICATE, () => NOW)
  server.vault = VECTOR_VAULT
  const store = await pinnedStore()
  return {
    server,
    store,
    context: { api: server, store, owner: VECTOR_OWNER, clock: { now: () => NOW } },
  }
}

async function failed(pending: Promise<unknown>): Promise<unknown> {
  return pending.then(
    () => undefined,
    (error: unknown) => error,
  )
}

describe('KEY-33 : restauration du trousseau sur un nouvel appareil (Restauration-Appareil)', () => {
  it('KEY-33 : la bonne clé de récupération ouvre le coffre et enregistre le trousseau, sans rien envoyer d’elle', async () => {
    const { server, store, context } = await scenario()
    server.certificate = VECTOR_CERTIFICATE

    await restoreKeys(context, parseRecoveryKey(vectorRecoveryKeyText()))

    expect(server.calls).toEqual(['readVault', 'readOwnKeys'])
    const restored = await store.loadKeyring(VECTOR_OWNER.userId)
    expect(restored?.subkeys).toHaveLength(1)
    await expect(store.loadCertificate(VECTOR_OWNER.userId)).resolves.toBeUndefined()
  })

  it('KEY-33 : sans certificat publié, le trousseau restauré s’enregistre ; la garde publiera l’identité', async () => {
    const { store, context } = await scenario()

    await restoreKeys(context, parseRecoveryKey(vectorRecoveryKeyText()))

    await expect(store.loadKeyring(VECTOR_OWNER.userId)).resolves.toBeDefined()
  })

  it('KEY-33 : une autre clé de récupération est refusée comme telle, sans rien enregistrer', async () => {
    const { store, context } = await scenario()

    expect(await failed(restoreKeys(context, generateRecoveryKey()))).toBeInstanceOf(
      WrongRecoveryKeyError,
    )
    await expect(store.loadKeyring(VECTOR_OWNER.userId)).resolves.toBeUndefined()
  })

  it('KEY-33, 16.1 : un certificat d’une autre clé d’identité que celle du coffre refuse le coffre', async () => {
    const { server, store, context } = await scenario()
    server.certificate = { ...VECTOR_CERTIFICATE, ikPublic: 'A'.repeat(43) }

    expect(
      await failed(restoreKeys(context, parseRecoveryKey(vectorRecoveryKeyText()))),
    ).toBeInstanceOf(InvalidVaultError)
    await expect(store.loadKeyring(VECTOR_OWNER.userId)).resolves.toBeUndefined()
  })

  it('PROTOCOL.md 5.3 : un ancien coffre rejoué, sans une sous-clé publiée, est refusé ; la version qui l’a s’ouvre', async () => {
    const replayed = await scenario()
    replayed.server.certificate = VECTOR_CERTIFICATE
    replayed.server.subkeys = [SECOND_SUBKEY]

    expect(
      await failed(restoreKeys(replayed.context, parseRecoveryKey(vectorRecoveryKeyText()))),
    ).toBeInstanceOf(InvalidVaultError)
    await expect(replayed.store.loadKeyring(VECTOR_OWNER.userId)).resolves.toBeUndefined()

    const current = await scenario()
    current.server.vault = VECTOR_NEXT_VAULT
    current.server.certificate = VECTOR_CERTIFICATE
    current.server.subkeys = [SECOND_SUBKEY]
    await restoreKeys(current.context, parseRecoveryKey(vectorRecoveryKeyText()))
    expect((await current.store.loadKeyring(VECTOR_OWNER.userId))?.subkeys).toHaveLength(2)
  })

  it('KEY-33 : un coffre absent ou altéré est refusé comme invalide', async () => {
    const absent = await scenario()
    absent.server.vault = undefined
    expect(
      await failed(restoreKeys(absent.context, parseRecoveryKey(vectorRecoveryKeyText()))),
    ).toBeInstanceOf(InvalidVaultError)

    const altered = await scenario()
    altered.server.vault = { ...VECTOR_VAULT, vaultVersion: 2 }
    expect(
      await failed(restoreKeys(altered.context, parseRecoveryKey(vectorRecoveryKeyText()))),
    ).toBeInstanceOf(InvalidVaultError)
  })

  it('ADR 0029 : un trousseau déjà restauré par un autre onglet est gardé, sans relire le coffre', async () => {
    const { server, context } = await scenario()
    await restoreKeys(context, parseRecoveryKey(vectorRecoveryKeyText()))
    server.calls.length = 0

    await restoreKeys(context, generateRecoveryKey())

    expect(server.calls).toEqual([])
  })
})
