import type { IKeysApi, VaultView } from '../api/IKeysApi'

/** KEY-32 : un autre appareil a écrit le coffre entre-temps ; la prochaine entrée mène à la restauration (ADR 0029). */
export class VaultTakenError extends Error {
  override readonly name = 'VaultTakenError'

  constructor() {
    super('coffre écrit par un autre appareil')
  }
}

/**
 * KEY-32, PROTOCOL.md 5.5 : écrit le coffre. Sur 409, le coffre relu identique au nôtre vient d'une requête dont la
 * réponse s'est perdue : il est écrit. Différent ou absent, il a été écrit par un autre : VaultTakenError.
 */
export async function saveVaultOnce(
  api: Pick<IKeysApi, 'saveVault' | 'readVault'>,
  vault: VaultView,
): Promise<void> {
  if ((await api.saveVault(vault)) === 'saved') {
    return
  }
  const stored = await api.readVault()
  if (stored === undefined || !sameVault(stored, vault)) {
    throw new VaultTakenError()
  }
}

function sameVault(left: VaultView, right: VaultView): boolean {
  return (
    left.vaultVersion === right.vaultVersion &&
    left.salt === right.salt &&
    left.nonceK === right.nonceK &&
    left.wrappedVdk === right.wrappedVdk &&
    left.nonceB === right.nonceB &&
    left.blob === right.blob
  )
}
