import { type NewKeyring, vaultToWire } from '@/shared/crypto'
import { completeKeys, type KeysContext } from './completeKeys'
import { saveVaultOnce, VaultTakenError } from './saveVault'

/**
 * KEY-02, ADR 0029 (point 2) : première connexion, une fois la clé de récupération confirmée. Le coffre est écrit en
 * premier, puis le trousseau enregistré sur l'appareil, puis la publication complétée (completeKeys) : rien n'est publié
 * sans coffre (ADR 0025, point 15). Un nouvel essai reprend avec le même trousseau : un coffre déjà écrit n'est pas
 * réécrit. « taken » : un autre appareil a écrit le premier coffre ; rien n'est enregistré ni publié ici. L'appelant
 * tient le verrou des clés.
 */
export async function setupKeys(
  context: KeysContext,
  pending: NewKeyring,
): Promise<'done' | 'taken'> {
  const { api, store, owner } = context
  if ((await store.loadKeyring(owner.userId)) === undefined) {
    try {
      await saveVaultOnce(api, vaultToWire(pending.vault))
    } catch (error) {
      if (error instanceof VaultTakenError) {
        return 'taken'
      }
      throw error
    }
    await store.saveKeyring(pending.keyring)
  }
  await completeKeys(context)
  return 'done'
}
