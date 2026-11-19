/**
 * Trousseau de l'appareil dans IndexedDB (KEY-40, ADR 0027) : IndexedDB clone les CryptoKey sans les rendre
 * extractables ; un script injecté pourrait s'en servir, mais pas les exfiltrer. Trois magasins : la clé de l'Autorité
 * épinglée (KEY-11, une pour l'appareil), les trousseaux et les certificats, chacun sous l'identifiant de son compte.
 * L'épinglage et l'écriture d'un certificat restent internes : seuls leur contrôle (KEY-11) et leur vérification
 * (KEY-06) les appellent (ADR 0027, point 12).
 */
import { type DBSchema, type IDBPDatabase, openDB } from 'idb'
import type { CertificateFields } from './identity'
import { createKeyring, type Keyring, keyringSecrets, type SubkeyInfo } from './keyring'

const DATABASE = 'seal'
const VERSION = 1
const AUTHORITY_KEY = 'ca'

/** Certificat d'identité tel que reçu, avec la signature de l'Autorité (PROTOCOL.md, section 4.2). */
export type StoredCertificate = CertificateFields & { readonly signature: Uint8Array }

/** Trousseau enregistré : ses données publiques et ses clés non extractables. */
interface StoredKeyring {
  readonly userId: string
  readonly identityPublicKey: Uint8Array<ArrayBuffer>
  readonly subkeys: readonly SubkeyInfo[]
  readonly createdAt: number
  readonly identityKey: CryptoKey
  readonly subkeyKeys: readonly CryptoKey[]
  readonly vdk: CryptoKey
}

interface SealDatabase extends DBSchema {
  authority: { key: string; value: { readonly publicKey: Uint8Array<ArrayBuffer> } }
  keyrings: { key: string; value: StoredKeyring }
  certificates: {
    key: string
    value: { readonly userId: string; readonly certificate: StoredCertificate }
  }
}

export interface KeyStore {
  loadKeyring(userId: string): Promise<Keyring | undefined>
  /** KEY-40 : refuse un trousseau dont une clé serait extractable. */
  saveKeyring(keyring: Keyring): Promise<void>
  loadCertificate(userId: string): Promise<StoredCertificate | undefined>
  /** KEY-11 : la clé épinglée, ou rien avant le premier usage. */
  loadAuthority(): Promise<Uint8Array<ArrayBuffer> | undefined>
  close(): void
}

/** Base de chaque trousseau ouvert par openKeyStore, hors de son interface publique. */
const DATABASES = new WeakMap<KeyStore, IDBPDatabase<SealDatabase>>()

export async function openKeyStore(name: string = DATABASE): Promise<KeyStore> {
  const database = await openDB<SealDatabase>(name, VERSION, {
    upgrade(db) {
      db.createObjectStore('authority')
      db.createObjectStore('keyrings', { keyPath: 'userId' })
      db.createObjectStore('certificates', { keyPath: 'userId' })
    },
  })
  const store: KeyStore = {
    async loadKeyring(userId) {
      const stored = await database.get('keyrings', userId)
      return stored === undefined
        ? undefined
        : createKeyring(stored, {
            identityKey: stored.identityKey,
            subkeyKeys: stored.subkeyKeys,
            vdk: stored.vdk,
          })
    },
    async saveKeyring(keyring) {
      const secrets = keyringSecrets(keyring)
      if (
        [secrets.identityKey, ...secrets.subkeyKeys, secrets.vdk].some((key) => key.extractable)
      ) {
        throw new TypeError(
          'KEY-40 : un trousseau ne s’enregistre qu’avec des clés non extractables',
        )
      }
      await database.put('keyrings', {
        userId: keyring.userId,
        identityPublicKey: keyring.identityPublicKey,
        subkeys: keyring.subkeys,
        createdAt: keyring.createdAt,
        ...secrets,
      })
      requestPersistence()
    },
    async loadCertificate(userId) {
      return (await database.get('certificates', userId))?.certificate
    },
    async loadAuthority() {
      const pinned = await database.get('authority', AUTHORITY_KEY)
      return pinned === undefined ? undefined : Uint8Array.from(pinned.publicKey)
    },
    close: () => {
      database.close()
    },
  }
  DATABASES.set(store, database)
  return store
}

/**
 * KEY-11 : épingle `publicKey` si rien ne l'est, dans une seule transaction, et rend la clé épinglée. Réservé à
 * checkAuthority, qui ne l'appelle qu'avec une clé cohérente (ADR 0027, points 5 et 12).
 */
export async function pinAuthority(
  store: KeyStore,
  publicKey: Uint8Array,
): Promise<Uint8Array<ArrayBuffer>> {
  const transaction = databaseOf(store).transaction('authority', 'readwrite')
  const pinned = await transaction.store.get(AUTHORITY_KEY)
  const kept = pinned?.publicKey ?? Uint8Array.from(publicKey)
  if (pinned === undefined) {
    await transaction.store.put({ publicKey: kept }, AUTHORITY_KEY)
  }
  await transaction.done
  return Uint8Array.from(kept)
}

/** KEY-06 : réservé à la vérification du certificat reçu, qui l'appelle après tous ses contrôles. */
export async function saveCertificate(
  store: KeyStore,
  userId: string,
  certificate: StoredCertificate,
): Promise<void> {
  await databaseOf(store).put('certificates', { userId, certificate })
}

function databaseOf(store: KeyStore): IDBPDatabase<SealDatabase> {
  const database = DATABASES.get(store)
  if (database === undefined) {
    throw new TypeError('trousseau inconnu : ouvrez-le par openKeyStore')
  }
  return database
}

/** ADR 0012 (Q6) : protège la base de l'éviction ; un refus ne change rien à l'enregistrement. */
function requestPersistence(): void {
  navigator.storage.persist().catch(() => undefined)
}
