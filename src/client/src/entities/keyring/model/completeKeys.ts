import {
  addSubkey,
  bindSubkey,
  compareOwnKeys,
  type Keyring,
  type KeyStore,
  neverPublished,
  proveIdentity,
  redateSubkey,
  reopenVault,
  storeOwnCertificate,
  type SubkeyInfo,
  VaultOpenError,
  vaultFromWire,
  vaultToWire,
  verifyOwnCertificate,
} from '@/shared/crypto'
import { ApiError, type Clock } from '@/shared/lib'
import type { IKeysApi, OwnKeysView } from '../api/IKeysApi'
import { saveVaultOnce } from './saveVault'

/** Titulaire des clés : le compte de la session (KEY-06 compare le certificat à lui). */
export interface KeyOwner {
  readonly userId: string
  readonly username: string
}

export interface KeysContext {
  readonly api: IKeysApi
  readonly store: KeyStore
  readonly owner: KeyOwner
  readonly clock: Clock
}

/** ADR 0029, point 3 : publiée à moins de 4 minutes de sa création, une sous-clé reste dans les 5 du serveur. */
const FRESH_SUBKEY_MS = 4 * 60_000

/** KEY-33, 16.1 : le serveur présente un certificat d'une autre clé d'identité que celle de l'appareil. */
export class ForeignIdentityError extends Error {
  override readonly name = 'ForeignIdentityError'

  constructor() {
    super('certificat d’une autre clé d’identité')
  }
}

/** PROTOCOL.md 5.5 : une sous-clé publiée manque au trousseau comme au coffre servi (coffre rejoué ou périmé). */
export class StaleVaultError extends Error {
  override readonly name = 'StaleVaultError'

  constructor(options?: ErrorOptions) {
    super('coffre périmé', options)
  }
}

/**
 * KEY-02, KEY-05, KEY-06, ADR 0029 (point 3) : complète la publication du trousseau enregistré sur l'appareil, d'où
 * qu'elle se soit arrêtée. Identité publiée si le serveur n'a pas de certificat ; certificat vérifié ; sous-clé publiée
 * si aucune du trousseau ne l'est, en version suivante du coffre si la plus récente a vieilli ; certificat enregistré en
 * dernier, qui marque la fin du parcours. Chaque étape relit l'état du serveur : rejouer est sans effet. L'appelant
 * tient le verrou des clés (withLock), que cette fonction ne redemande pas.
 */
export async function completeKeys(context: KeysContext): Promise<void> {
  const { store, owner, clock } = context
  let keyring = await store.loadKeyring(owner.userId)
  if (keyring === undefined) {
    throw new TypeError('KEY-02 : aucun trousseau à compléter sur cet appareil')
  }
  const served = await servedKeys(context, keyring)
  let comparison = compareOwnKeys(keyring, served)
  if (comparison.kind === 'foreign') {
    throw new ForeignIdentityError()
  }
  if (comparison.kind === 'stale') {
    keyring = await refreshed(context, keyring, served)
    comparison = compareOwnKeys(keyring, served)
  }
  const expected = { ...owner, ikPublic: keyring.identityPublicKey }
  await verifyOwnCertificate(store, served.certificate, expected, clock.now())
  if (comparison.kind === 'match' && !comparison.published) {
    await publishSubkey(context, keyring, served)
  }
  await storeOwnCertificate(store, served.certificate, expected, clock.now())
}

/** KEY-03, KEY-12 : les clés publiées ; l'identité l'est d'abord si le serveur n'a aucun certificat courant. */
async function servedKeys({ api, owner }: KeysContext, keyring: Keyring): Promise<OwnKeysView> {
  const served = await api.readOwnKeys(owner.userId)
  if (served !== undefined) {
    return served
  }
  try {
    const certificate = await api.publishIdentity(
      await proveIdentity(keyring, await api.createChallenge()),
    )
    return { certificate, subkeys: [] }
  } catch (error) {
    // ADR 0025, point 4 : 403 quand un certificat valide existe déjà, publié par une requête dont la réponse s'est
    // perdue ou par un autre onglet : il se relit.
    if (error instanceof ApiError && error.status === 403) {
      const published = await api.readOwnKeys(owner.userId)
      if (published !== undefined) {
        return published
      }
    }
    throw error
  }
}

/** PROTOCOL.md 5.5 : une sous-clé publiée manque au trousseau ; le coffre servi, rouvert par la VDK, doit l'avoir. */
async function refreshed(
  { api, store }: KeysContext,
  keyring: Keyring,
  served: OwnKeysView,
): Promise<Keyring> {
  const reopened = await reopenedFromServer(api, keyring)
  if (compareOwnKeys(reopened, served).kind !== 'match') {
    throw new StaleVaultError()
  }
  await store.saveKeyring(reopened)
  return reopened
}

/**
 * KEY-05, KEY-32 : publie la sous-clé la plus récente si elle a moins de 4 minutes. Sinon, elle est d'abord écrite dans
 * une version suivante du coffre (ADR 0025, point 15) : redatée si elle n'a jamais été publiée, pour que des reprises
 * qui échouent ne fassent pas grossir le coffre, ou remplacée par une neuve, ajoutée (ADR 0029, point 11).
 */
async function publishSubkey(
  { api, store, clock }: KeysContext,
  keyring: Keyring,
  served: OwnKeysView,
): Promise<void> {
  const now = clock.now()
  let current = keyring
  let subkey = newest(current.subkeys)
  if (subkey === undefined || Math.abs(now - subkey.createdAt) > FRESH_SUBKEY_MS) {
    const wire = await api.readVault()
    if (wire === undefined) {
      throw new StaleVaultError()
    }
    const vault = vaultFromWire(wire)
    const grown = await mapVaultErrors(
      subkey !== undefined && neverPublished(served, now)(subkey)
        ? redateSubkey(current, vault, subkey.ekId, now)
        : addSubkey(current, vault, now),
    )
    await saveVaultOnce(api, vaultToWire(grown.vault))
    await store.saveKeyring(grown.keyring)
    current = grown.keyring
    subkey = newest(current.subkeys)
  }
  if (subkey === undefined) {
    throw new StaleVaultError()
  }
  await api.publishSubkey(await bindSubkey(current, served.certificate.serial, subkey.ekId))
}

async function reopenedFromServer(api: IKeysApi, keyring: Keyring): Promise<Keyring> {
  const wire = await api.readVault()
  if (wire === undefined) {
    throw new StaleVaultError()
  }
  return mapVaultErrors(reopenVault(keyring, vaultFromWire(wire)))
}

/** Un coffre servi illisible, ou qui perdrait une sous-clé de l'appareil, est un coffre périmé ou rejoué. */
async function mapVaultErrors<T>(pending: Promise<T>): Promise<T> {
  try {
    return await pending
  } catch (error) {
    throw error instanceof VaultOpenError ? new StaleVaultError({ cause: error }) : error
  }
}

function newest(subkeys: readonly SubkeyInfo[]): SubkeyInfo | undefined {
  return subkeys.reduce<SubkeyInfo | undefined>(
    (latest, subkey) =>
      latest === undefined || subkey.createdAt > latest.createdAt ? subkey : latest,
    undefined,
  )
}
