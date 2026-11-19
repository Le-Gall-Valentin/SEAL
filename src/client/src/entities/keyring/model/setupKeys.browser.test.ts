import { describe, expect, it } from 'vitest'
import { vaultFromWire } from '@/shared/crypto'
import { setupKeys } from './setupKeys'
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

/** Le trousseau et le premier coffre des vecteurs, tels que generateKeyring les rend à « Terminer ». */
async function generated() {
  return { keyring: await vectorKeyring(), vault: vaultFromWire(VECTOR_VAULT) }
}

async function scenario() {
  const server = fakeKeysServer(VECTOR_CERTIFICATE, () => NOW)
  const store = await pinnedStore()
  return {
    server,
    store,
    context: { api: server, store, owner: VECTOR_OWNER, clock: { now: () => NOW } },
  }
}

describe('KEY-02 : première connexion, une fois la clé de récupération confirmée (ADR 0029, point 2)', () => {
  it('KEY-02, ADR 0025 (point 15) : le coffre est écrit et le trousseau enregistré avant toute publication', async () => {
    const { server, store, context } = await scenario()

    await expect(setupKeys(context, await generated())).resolves.toBe('done')

    expect(server.calls).toEqual([
      'saveVault',
      'readOwnKeys',
      'createChallenge',
      'publishIdentity',
      'publishSubkey',
    ])
    expect(server.vault).toEqual(VECTOR_VAULT)
    await expect(store.loadCertificate(VECTOR_OWNER.userId)).resolves.toBeDefined()
  })

  it('KEY-32 : un PUT du coffre dont la réponse s’est perdue se rejoue : 409, coffre relu identique, et la suite', async () => {
    const { server, context } = await scenario()
    const pending = await generated()
    server.fail('saveVault', 'after')

    await expect(setupKeys(context, pending)).rejects.toThrow()
    await expect(setupKeys(context, pending)).resolves.toBe('done')

    expect(server.calls.filter((call) => call === 'publishIdentity')).toHaveLength(1)
  })

  it('KEY-02 : un coffre déjà écrit par un autre appareil : rien n’est enregistré ni publié', async () => {
    const { server, store, context } = await scenario()
    server.vault = VECTOR_NEXT_VAULT

    await expect(setupKeys(context, await generated())).resolves.toBe('taken')

    expect(server.calls).toEqual(['saveVault', 'readVault'])
    await expect(store.loadKeyring(VECTOR_OWNER.userId)).resolves.toBeUndefined()
  })

  it('ADR 0029 : un nouvel essai après un trousseau déjà enregistré reprend la publication, sans réécrire le coffre', async () => {
    const { server, context } = await scenario()
    const pending = await generated()
    server.fail('publishIdentity', 'before')

    await expect(setupKeys(context, pending)).rejects.toThrow()
    await expect(setupKeys(context, pending)).resolves.toBe('done')

    expect(server.calls.filter((call) => call === 'saveVault')).toHaveLength(1)
  })
})
