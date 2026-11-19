import { describe, expect, it } from 'vitest'
import type { IKeysApi, VaultView } from '../api/IKeysApi'
import { saveVaultOnce, VaultTakenError } from './saveVault'

const VAULT: VaultView = {
  vaultVersion: 1,
  salt: 'S'.repeat(43),
  nonceK: 'K'.repeat(16),
  wrappedVdk: 'W'.repeat(64),
  nonceB: 'B'.repeat(16),
  blob: 'X'.repeat(22),
}

function api(
  save: 'saved' | 'conflict',
  stored: VaultView | undefined,
): Pick<IKeysApi, 'saveVault' | 'readVault'> {
  return {
    saveVault: () => Promise.resolve(save),
    readVault: () => Promise.resolve(stored),
  }
}

describe('KEY-32 : écriture du coffre, une fois (PROTOCOL.md 5.5, ADR 0029 point 3)', () => {
  it('KEY-32 : un coffre accepté est écrit', async () => {
    await expect(saveVaultOnce(api('saved', undefined), VAULT)).resolves.toBeUndefined()
  })

  it('KEY-32 : un 409 dont le coffre relu est le nôtre vaut écriture (réponse perdue, requête rejouée)', async () => {
    await expect(saveVaultOnce(api('conflict', { ...VAULT }), VAULT)).resolves.toBeUndefined()
  })

  it('KEY-32 : un 409 dont le coffre relu diffère, ou manque, signale qu’un autre appareil l’a écrit', async () => {
    for (const stored of [
      { ...VAULT, blob: 'Y'.repeat(22) },
      { ...VAULT, vaultVersion: 2 },
      undefined,
    ]) {
      await expect(saveVaultOnce(api('conflict', stored), VAULT)).rejects.toBeInstanceOf(
        VaultTakenError,
      )
    }
  })
})
