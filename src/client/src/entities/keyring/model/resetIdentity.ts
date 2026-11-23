import {
  compareOwnKeys,
  generateKeyring,
  type Keyring,
  type NewKeyring,
  proveIdentity,
  type RecoveryKey,
  vaultToWire,
} from '@/shared/crypto'
import { ApiError } from '@/shared/lib'
import type { IKeysApi } from '../api/IKeysApi'
import { completeKeys, type KeyOwner, type KeysContext } from './completeKeys'
import { InvalidVaultError } from './restoreKeys'

/**
 * KEY-34, ADR 0036 (point 2) : nouvelles IK, EK et VDK, et leur coffre sous la nouvelle clé de récupération, en version
 * suivant celle du coffre enregistré, qu'il remplacera. Sans coffre, il n'y a rien à réinitialiser. Rien n'est envoyé.
 */
export async function prepareReset(
  { api, owner, clock }: KeysContext,
  recoveryKey: RecoveryKey,
): Promise<NewKeyring> {
  const stored = await api.readVault()
  if (stored === undefined) {
    throw new InvalidVaultError()
  }
  return generateKeyring(owner.userId, recoveryKey, clock.now(), stored.vaultVersion + 1)
}

/**
 * KEY-34, ADR 0036 : envoie la nouvelle identité préparée, avec un défi neuf ; le trousseau n'est enregistré sur
 * l'appareil qu'une fois la réinitialisation acquise, puis completeKeys publie la sous-clé et enregistre le certificat,
 * comme à la première connexion. « taken » : le coffre a changé entre-temps (409), ou un autre trousseau est déjà sur
 * l'appareil ; rien n'est enregistré. 403 REAUTH_REQUIRED remonte : l'appelant relance avec le même `pending` après la
 * ré-authentification. L'appelant tient le verrou des clés.
 */
export async function resetIdentity(
  context: KeysContext,
  pending: NewKeyring,
): Promise<'done' | 'taken'> {
  const { store, owner } = context
  const stored = await store.loadKeyring(owner.userId)
  if (stored === undefined) {
    if ((await sendReset(context, pending)) === 'taken') {
      return 'taken'
    }
    await store.saveKeyring(pending.keyring)
  } else if (!sameIdentity(stored, pending.keyring)) {
    // ADR 0029, point 3 : un autre onglet a restauré ou réinitialisé pendant que ce parcours attendait.
    return 'taken'
  }
  await completeKeys(context)
  return 'done'
}

async function sendReset(
  { api, owner }: KeysContext,
  { keyring, vault }: NewKeyring,
): Promise<'sent' | 'taken'> {
  try {
    const proof = await proveIdentity(keyring, await api.createChallenge())
    const result = await api.resetIdentity({ ...proof, vault: vaultToWire(vault) })
    return result === 'conflict' ? 'taken' : 'sent'
  } catch (error) {
    // Réponse perdue ou erreur du serveur : la transaction a pu aboutir ; le certificat servi le dit (ADR 0036).
    const maybeApplied = !(error instanceof ApiError && error.status < 500)
    if (maybeApplied && (await serves(api, owner, keyring).catch(() => false))) {
      return 'sent'
    }
    throw error
  }
}

/** Vrai si le certificat courant du serveur porte l'IK de `keyring`. */
async function serves(api: IKeysApi, owner: KeyOwner, keyring: Keyring): Promise<boolean> {
  const served = await api.readOwnKeys(owner.userId)
  return served !== undefined && compareOwnKeys(keyring, served).kind !== 'foreign'
}

function sameIdentity(left: Keyring, right: Keyring): boolean {
  const a = left.identityPublicKey
  const b = right.identityPublicKey
  return a.length === b.length && a.every((byte, index) => byte === b[index])
}
