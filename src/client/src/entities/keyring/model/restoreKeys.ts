import {
  compareOwnKeys,
  type Keyring,
  openVault,
  type RecoveryKey,
  VaultOpenError,
  vaultFromWire,
} from '@/shared/crypto'
import type { KeysContext } from './completeKeys'

/** KEY-33 : la clé saisie n'ouvre pas le coffre du compte. */
export class WrongRecoveryKeyError extends Error {
  override readonly name = 'WrongRecoveryKeyError'

  constructor() {
    super('clé de récupération refusée')
  }
}

/** KEY-33 : coffre absent, altéré, rejoué ou d'une autre identité que le certificat (ADR 0029, point 5). */
export class InvalidVaultError extends Error {
  override readonly name = 'InvalidVaultError'

  constructor(options?: ErrorOptions) {
    super('coffre invalide', options)
  }
}

/**
 * KEY-33 : nouvel appareil. Le coffre du serveur s'ouvre par la clé de récupération, ici, sans qu'elle soit envoyée ;
 * son IK doit être celle du certificat publié, et il doit contenir toutes les sous-clés publiées (PROTOCOL.md 5.3 :
 * un ancien coffre rejoué est refusé). Le trousseau s'enregistre alors, clés non extractables ; la garde de la zone du
 * compte complète ensuite la publication (completeKeys). Un trousseau déjà présent, restauré par un autre onglet, est
 * gardé. L'appelant tient le verrou des clés.
 */
export async function restoreKeys(context: KeysContext, recoveryKey: RecoveryKey): Promise<void> {
  const { api, store, owner } = context
  if ((await store.loadKeyring(owner.userId)) !== undefined) {
    return
  }
  const wire = await api.readVault()
  if (wire === undefined) {
    throw new InvalidVaultError()
  }
  const keyring = await opened(owner.userId, wire, recoveryKey)
  const served = await api.readOwnKeys(owner.userId)
  if (served !== undefined && compareOwnKeys(keyring, served).kind !== 'match') {
    throw new InvalidVaultError()
  }
  await store.saveKeyring(keyring)
}

async function opened(
  userId: string,
  wire: Parameters<typeof vaultFromWire>[0],
  recoveryKey: RecoveryKey,
): Promise<Keyring> {
  try {
    return await openVault(userId, vaultFromWire(wire), recoveryKey)
  } catch (error) {
    if (error instanceof VaultOpenError) {
      throw error.reason === 'key'
        ? new WrongRecoveryKeyError()
        : new InvalidVaultError({ cause: error })
    }
    throw error
  }
}
