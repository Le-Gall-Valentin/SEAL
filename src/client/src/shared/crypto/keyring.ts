/**
 * Trousseau du client (CDC, sections 6.4 et 6.5 ; PROTOCOL.md, section 5.2 ; KEY-40, ADR 0027). IK et EK ne sont
 * extractables que le temps de construire le coffre : la génération les exporte en PKCS#8, scelle le coffre, puis les
 * réimporte non extractables et remet les tampons à zéro. À l'ouverture, le trousseau est relu strictement et ses clés
 * importées non extractables ; leurs clés publiques se relisent dans le JWK de la clé importée en mémoire.
 */
import { z } from 'zod'
import { fromBase64Url, toBase64Url } from './base64url'
import { KEM, type Kem } from './identity'
import { generateRecoveryKey, type RecoveryKey } from './recovery'
import { openVaultBytes, sealVault, type VaultPayload, VaultOpenError } from './vault'
import { generateX25519 } from './webcrypto'

const FORMAT_VERSION = 1
const EK_ID_BYTES = 16
const VDK_BYTES = 32
/** KEY-21 : une sous-clé vaut 45 jours. */
const SUBKEY_VALIDITY_MS = 45 * 86_400_000

export interface Subkey {
  readonly ekId: Uint8Array<ArrayBuffer>
  readonly kem: Kem
  readonly privateKey: CryptoKey
  readonly publicKey: Uint8Array<ArrayBuffer>
  readonly createdAt: number
  readonly expiresAt: number
}

/** Trousseau en mémoire : clés privées et VDK non extractables (KEY-40). */
export interface Keyring {
  readonly userId: string
  readonly identity: { readonly privateKey: CryptoKey; readonly publicKey: Uint8Array<ArrayBuffer> }
  readonly subkeys: readonly Subkey[]
  readonly vdk: CryptoKey
  readonly createdAt: number
}

export interface NewKeyring {
  readonly keyring: Keyring
  readonly recoveryKey: RecoveryKey
  readonly vault: VaultPayload
}

export interface SubkeyExport {
  readonly ekId: Uint8Array
  readonly kem: Kem
  readonly pkcs8: Uint8Array
  readonly createdAt: number
  readonly expiresAt: number
}

/** Trousseau en clair, le temps de construire le coffre : PKCS#8 en mémoire seulement (KEY-40). */
export interface KeyringExport {
  readonly userId: string
  readonly ik: Uint8Array
  readonly eks: readonly SubkeyExport[]
  readonly createdAt: number
}

const bytes = z.string().regex(/^[A-Za-z0-9_-]*$/)
const millis = z.number().int().nonnegative().max(Number.MAX_SAFE_INTEGER)

/** PROTOCOL.md 5.2 : un champ inconnu, un autre format ou une autre valeur de kem sont refusés. */
const KeyringJson = z.strictObject({
  v: z.literal(FORMAT_VERSION),
  userId: z.string(),
  ik: bytes,
  eks: z
    .array(
      z.strictObject({
        ekId: bytes,
        kem: z.literal(KEM),
        pkcs8: bytes,
        createdAt: millis,
        expiresAt: millis,
      }),
    )
    .min(1),
  createdAt: millis,
})

/** PROTOCOL.md 5.2 : JSON compact, champs dans l'ordre du protocole. */
export function serializeKeyring(keyring: KeyringExport): Uint8Array<ArrayBuffer> {
  return new TextEncoder().encode(
    JSON.stringify({
      v: FORMAT_VERSION,
      userId: keyring.userId,
      ik: toBase64Url(keyring.ik),
      eks: keyring.eks.map((subkey) => ({
        ekId: toBase64Url(subkey.ekId),
        kem: subkey.kem,
        pkcs8: toBase64Url(subkey.pkcs8),
        createdAt: subkey.createdAt,
        expiresAt: subkey.expiresAt,
      })),
      createdAt: keyring.createdAt,
    }),
  )
}

/**
 * KEY-02, KEY-40 : IK, EK et VDK neuves, la clé de récupération et le premier coffre (version 1). Rien n'est publié
 * ni enregistré ici : le parcours de première connexion le fera, coffre d'abord.
 */
export async function generateKeyring(userId: string, now: number): Promise<NewKeyring> {
  const ik = await crypto.subtle.generateKey({ name: 'Ed25519' }, true, ['sign', 'verify'])
  const ek = await generateX25519(true, ['deriveBits'])
  const ikPkcs8 = new Uint8Array(await crypto.subtle.exportKey('pkcs8', ik.privateKey))
  const ekPkcs8 = new Uint8Array(await crypto.subtle.exportKey('pkcs8', ek.privateKey))
  const vdk = crypto.getRandomValues(new Uint8Array(VDK_BYTES))
  const subkey: Omit<Subkey, 'privateKey' | 'publicKey'> = {
    ekId: crypto.getRandomValues(new Uint8Array(EK_ID_BYTES)),
    kem: KEM,
    createdAt: now,
    expiresAt: now + SUBKEY_VALIDITY_MS,
  }
  const plaintext = serializeKeyring({
    userId,
    ik: ikPkcs8,
    eks: [{ ...subkey, pkcs8: ekPkcs8 }],
    createdAt: now,
  })
  try {
    const recoveryKey = generateRecoveryKey()
    const vault = await sealVault({ userId, vaultVersion: 1, recoveryKey, vdk, plaintext })
    const keyring: Keyring = {
      userId,
      identity: {
        privateKey: await crypto.subtle.importKey('pkcs8', ikPkcs8, 'Ed25519', false, ['sign']),
        publicKey: new Uint8Array(await crypto.subtle.exportKey('raw', ik.publicKey)),
      },
      subkeys: [
        {
          ...subkey,
          privateKey: await crypto.subtle.importKey('pkcs8', ekPkcs8, 'X25519', false, [
            'deriveBits',
          ]),
          publicKey: new Uint8Array(await crypto.subtle.exportKey('raw', ek.publicKey)),
        },
      ],
      vdk: await crypto.subtle.importKey('raw', vdk, 'AES-GCM', false, ['encrypt', 'decrypt']),
      createdAt: now,
    }
    return { keyring, recoveryKey, vault }
  } finally {
    ikPkcs8.fill(0)
    ekPkcs8.fill(0)
    vdk.fill(0)
    plaintext.fill(0)
  }
}

/**
 * KEY-33 : ouvre le coffre par RK et rend le trousseau, clés non extractables. Une RK fausse, un coffre d'un autre
 * compte ou d'une autre version, ou un trousseau mal formé lèvent VaultOpenError, sans trousseau partiel.
 */
export async function openVault(
  userId: string,
  payload: VaultPayload,
  recoveryKey: RecoveryKey,
): Promise<Keyring> {
  const { vdk, plaintext } = await openVaultBytes(userId, payload, recoveryKey)
  try {
    const json = KeyringJson.parse(
      JSON.parse(new TextDecoder('utf-8', { fatal: true }).decode(plaintext)),
    )
    if (json.userId !== userId) {
      throw new VaultOpenError('trousseau d’un autre compte')
    }
    const identity = await importPrivate(fromBase64Url(json.ik), 'Ed25519', ['sign'])
    const subkeys = await Promise.all(
      json.eks.map(async (subkey): Promise<Subkey> => {
        const ekId = fromBase64Url(subkey.ekId)
        if (ekId.length !== EK_ID_BYTES) {
          throw new VaultOpenError('ekId mal formé')
        }
        const key = await importPrivate(fromBase64Url(subkey.pkcs8), 'X25519', ['deriveBits'])
        return {
          ...key,
          ekId,
          kem: subkey.kem,
          createdAt: subkey.createdAt,
          expiresAt: subkey.expiresAt,
        }
      }),
    )
    return { userId, identity, subkeys, vdk, createdAt: json.createdAt }
  } catch (error) {
    throw error instanceof VaultOpenError
      ? error
      : new VaultOpenError('trousseau illisible', { cause: error })
  } finally {
    plaintext.fill(0)
  }
}

/** Clé privée non extractable et sa clé publique, relue dans le JWK d'une importation passagère en mémoire. */
async function importPrivate(
  pkcs8: Uint8Array<ArrayBuffer>,
  algorithm: 'Ed25519' | 'X25519',
  usages: KeyUsage[],
): Promise<{ privateKey: CryptoKey; publicKey: Uint8Array<ArrayBuffer> }> {
  try {
    const passing = await crypto.subtle.importKey('pkcs8', pkcs8, algorithm, true, usages)
    const { x } = await crypto.subtle.exportKey('jwk', passing)
    if (x === undefined) {
      throw new VaultOpenError('clé publique absente du JWK')
    }
    return {
      privateKey: await crypto.subtle.importKey('pkcs8', pkcs8, algorithm, false, usages),
      publicKey: fromBase64Url(x),
    }
  } finally {
    pkcs8.fill(0)
  }
}
