import { describe, expect, it } from 'vitest'
import { generateKeyring, generateRecoveryKey, openVault, vaultFromWire } from '@/shared/crypto'
import { ApiError, NetworkError } from '@/shared/lib'
import { InvalidVaultError } from './restoreKeys'
import { prepareReset, resetIdentity } from './resetIdentity'
import { fakeKeysServer } from './testing/fakeKeysServer'
import {
  pinnedStore,
  VECTOR_CERTIFICATE,
  VECTOR_NEXT_VAULT,
  VECTOR_OWNER,
  VECTOR_SUBKEY_CREATED_AT,
  VECTOR_VAULT,
  vectorKeyring,
} from './testing/keyVectors'

const NOW = VECTOR_SUBKEY_CREATED_AT + 60_000

/** Certificat de l'identité perdue : une autre IK, un autre numéro de série. */
const LOST_CERTIFICATE = {
  ...VECTOR_CERTIFICATE,
  serial: 'BBBBBBBBBBBBBBBBBBBBBB',
  ikPublic: 'B'.repeat(43),
}

/** La nouvelle identité : le trousseau des vecteurs, et son coffre en version 2, qui suit celui de l'identité perdue. */
async function pending() {
  return { keyring: await vectorKeyring(), vault: vaultFromWire(VECTOR_NEXT_VAULT) }
}

async function scenario() {
  const server = fakeKeysServer(VECTOR_CERTIFICATE, () => NOW)
  server.certificate = LOST_CERTIFICATE
  server.vault = { ...VECTOR_VAULT, blob: 'C'.repeat(22) }
  const store = await pinnedStore()
  return {
    server,
    store,
    context: { api: server, store, owner: VECTOR_OWNER, clock: { now: () => NOW } },
  }
}

describe('KEY-34 : réinitialisation de l’identité (ADR 0036)', () => {
  it('KEY-34 : la nouvelle identité remplace l’ancienne, puis la publication se termine comme à la première connexion', async () => {
    const { server, store, context } = await scenario()

    await expect(resetIdentity(context, await pending())).resolves.toBe('done')

    expect(server.calls).toEqual([
      'createChallenge',
      'resetIdentity',
      'readOwnKeys',
      'publishSubkey',
    ])
    expect(server.vault).toEqual(VECTOR_NEXT_VAULT)
    expect(server.certificate).toEqual(VECTOR_CERTIFICATE)
    await expect(store.loadKeyring(VECTOR_OWNER.userId)).resolves.toBeDefined()
    await expect(store.loadCertificate(VECTOR_OWNER.userId)).resolves.toBeDefined()
  })

  it('ADR 0036 : réponse perdue après la réinitialisation, le certificat servi le prouve et le parcours se termine', async () => {
    const { server, store, context } = await scenario()
    server.fail('resetIdentity', 'after')

    await expect(resetIdentity(context, await pending())).resolves.toBe('done')

    expect(server.calls.slice(0, 3)).toEqual(['createChallenge', 'resetIdentity', 'readOwnKeys'])
    await expect(store.loadCertificate(VECTOR_OWNER.userId)).resolves.toBeDefined()
  })

  it('ADR 0036 : réponse perdue et relecture ratée, la relance reçoit 409 mais relit le certificat : le parcours se termine', async () => {
    const { server, store, context } = await scenario()
    const keys = await pending()
    server.fail('resetIdentity', 'after')
    server.fail('readOwnKeys', 'before')

    await expect(resetIdentity(context, keys)).rejects.toBeInstanceOf(NetworkError)
    await expect(store.loadKeyring(VECTOR_OWNER.userId)).resolves.toBeUndefined()
    await expect(resetIdentity(context, keys)).resolves.toBe('done')

    expect(server.calls.filter((call) => call === 'resetIdentity')).toHaveLength(2)
    await expect(store.loadCertificate(VECTOR_OWNER.userId)).resolves.toBeDefined()
  })

  it('ADR 0036 : un 200 au corps illisible n’est pas un refus, le certificat servi est relu', async () => {
    const { server, store, context } = await scenario()
    const api = {
      ...server,
      resetIdentity: async (body: Parameters<typeof server.resetIdentity>[0]) => {
        await server.resetIdentity(body)
        throw new ApiError(200, 'INTERNAL_ERROR')
      },
    }

    await expect(resetIdentity({ ...context, api }, await pending())).resolves.toBe('done')

    await expect(store.loadCertificate(VECTOR_OWNER.userId)).resolves.toBeDefined()
  })

  it('ADR 0036 : échec avant la réinitialisation, l’erreur remonte et rien n’est enregistré sur l’appareil', async () => {
    const { server, store, context } = await scenario()
    server.fail('resetIdentity', 'before')

    await expect(resetIdentity(context, await pending())).rejects.toBeInstanceOf(NetworkError)

    expect(server.certificate).toEqual(LOST_CERTIFICATE)
    await expect(store.loadKeyring(VECTOR_OWNER.userId)).resolves.toBeUndefined()
  })

  it('ADR 0036 (point 2) : coffre changé entre-temps (409), taken, et rien n’est enregistré', async () => {
    const { server, store, context } = await scenario()
    server.vault = VECTOR_NEXT_VAULT

    await expect(resetIdentity(context, await pending())).resolves.toBe('taken')

    expect(server.certificate).toEqual(LOST_CERTIFICATE)
    await expect(store.loadKeyring(VECTOR_OWNER.userId)).resolves.toBeUndefined()
  })

  it('AUTH-55 : hors du mode sudo, 403 remonte sans rien enregistrer ; la relance avec la même clé prend un défi neuf', async () => {
    const { server, store, context } = await scenario()
    const keys = await pending()
    server.closeSudoOnce()

    await expect(resetIdentity(context, keys)).rejects.toEqual(new ApiError(403, 'REAUTH_REQUIRED'))
    await expect(store.loadKeyring(VECTOR_OWNER.userId)).resolves.toBeUndefined()

    await expect(resetIdentity(context, keys)).resolves.toBe('done')
    expect(server.calls.filter((call) => call === 'createChallenge')).toHaveLength(2)
  })

  it('ADR 0029 (point 3) : publication interrompue après la réinitialisation, la reprise la termine sans renvoyer l’identité', async () => {
    const { server, store, context } = await scenario()
    const keys = await pending()
    server.fail('publishSubkey', 'before')

    await expect(resetIdentity(context, keys)).rejects.toBeInstanceOf(NetworkError)
    await expect(store.loadKeyring(VECTOR_OWNER.userId)).resolves.toBeDefined()
    await expect(resetIdentity(context, keys)).resolves.toBe('done')

    expect(server.calls.filter((call) => call === 'resetIdentity')).toHaveLength(1)
    await expect(store.loadCertificate(VECTOR_OWNER.userId)).resolves.toBeDefined()
  })

  it('ADR 0029 (point 3) : un autre trousseau déjà enregistré sur l’appareil, taken, sans requête', async () => {
    const { server, store, context } = await scenario()
    const other = await generateKeyring(VECTOR_OWNER.userId, generateRecoveryKey(), NOW)
    await store.saveKeyring(other.keyring)

    await expect(resetIdentity(context, await pending())).resolves.toBe('taken')

    expect(server.calls).toEqual([])
  })

  it('KEY-34, ADR 0036 (point 2) : la préparation génère un coffre en version suivante, ouvert par la nouvelle clé', async () => {
    const { server, context } = await scenario()
    server.vault = { ...VECTOR_VAULT, vaultVersion: 4 }
    const recoveryKey = generateRecoveryKey()

    const prepared = await prepareReset(context, recoveryKey)

    expect(prepared.vault.vaultVersion).toBe(5)
    const opened = await openVault(VECTOR_OWNER.userId, prepared.vault, recoveryKey)
    expect(opened.identityPublicKey).toEqual(prepared.keyring.identityPublicKey)
  })

  it('KEY-34 : sans coffre sur le serveur, rien à réinitialiser', async () => {
    const { server, context } = await scenario()
    server.vault = undefined

    await expect(prepareReset(context, generateRecoveryKey())).rejects.toBeInstanceOf(
      InvalidVaultError,
    )
  })
})
