/**
 * Coffre de clés (CDC, section 6.4 ; PROTOCOL.md, section 5 ; KEY-31, ADR 0027). La VDK chiffre le trousseau, la KEK
 * tirée de RK par HKDF chiffre la VDK ; chaque AAD attache le niveau au compte, et le blob à sa version. À l'ouverture,
 * la VDK sort d'unwrapKey déjà non extractable (KEY-40) ; toute erreur devient VaultOpenError, sans résultat partiel.
 */
import { struct, u64, uuid } from './canonical'
import { type RecoveryKey, recoveryKeyBytes } from './recovery'

const SALT_BYTES = 32
const NONCE_BYTES = 12
const VDK_BYTES = 32
const WRAPPED_VDK_BYTES = VDK_BYTES + 16

/** Corps de PUT /api/v1/keys/vault, octets décodés (ADR 0025, point 6). */
export interface VaultPayload {
  readonly vaultVersion: number
  readonly salt: Uint8Array<ArrayBuffer>
  readonly nonceK: Uint8Array<ArrayBuffer>
  readonly wrappedVdk: Uint8Array<ArrayBuffer>
  readonly nonceB: Uint8Array<ArrayBuffer>
  readonly blob: Uint8Array<ArrayBuffer>
}

export interface VaultNonces {
  readonly salt: Uint8Array<ArrayBuffer>
  readonly nonceK: Uint8Array<ArrayBuffer>
  readonly nonceB: Uint8Array<ArrayBuffer>
}

export interface SealInput {
  readonly userId: string
  readonly vaultVersion: number
  readonly recoveryKey: RecoveryKey
  /** VDK brute, 32 octets : seule la fonction qui construit le coffre la tient (KEY-40). */
  readonly vdk: Uint8Array<ArrayBuffer>
  readonly plaintext: Uint8Array<ArrayBuffer>
  /** Fixés par les vecteurs seulement ; tirés au hasard sinon. */
  readonly nonces?: VaultNonces
}

export interface OpenedVault {
  readonly vdk: CryptoKey
  readonly plaintext: Uint8Array<ArrayBuffer>
}

/**
 * Étage qui refuse un coffre (PROTOCOL.md 5.4) : format (tailles ou version), key (la VDK ne se désenveloppe pas),
 * blob (le trousseau ne se déchiffre pas) ou keyring (sa relecture stricte le refuse).
 */
export type VaultRejection = 'format' | 'key' | 'blob' | 'keyring'

/** Ne porte que son étage : jamais un octet de RK, de la VDK ni du trousseau. */
export class VaultOpenError extends Error {
  override readonly name = 'VaultOpenError'
  readonly reason: VaultRejection

  constructor(reason: VaultRejection, options?: ErrorOptions) {
    super(`coffre refusé : ${reason}`, options)
    this.reason = reason
  }
}

export function vaultAad(userId: string, vaultVersion: number): Uint8Array<ArrayBuffer> {
  return struct('SEAL-vault-v1', uuid(userId), u64(vaultVersion))
}

export function kekInfo(userId: string): Uint8Array<ArrayBuffer> {
  return struct('SEAL-kek-v1', uuid(userId))
}

export function kekAad(userId: string): Uint8Array<ArrayBuffer> {
  return struct('SEAL-kek-aad-v1', uuid(userId))
}

/** KEY-31 : HKDF-SHA256 suffit, RK ayant 160 bits aléatoires. */
export function kekParameters(salt: Uint8Array<ArrayBuffer>, userId: string): HkdfParams {
  return { name: 'HKDF', hash: 'SHA-256', salt, info: kekInfo(userId) }
}

export async function sealVault(input: SealInput): Promise<VaultPayload> {
  if (input.vdk.length !== VDK_BYTES) {
    throw new RangeError('la VDK fait 32 octets')
  }
  const { salt, nonceK, nonceB } = input.nonces ?? freshNonces()
  const kek = await deriveKek(input.recoveryKey, salt, input.userId, ['encrypt'])
  const wrappedVdk = await crypto.subtle.encrypt(
    { name: 'AES-GCM', iv: nonceK, additionalData: kekAad(input.userId) },
    kek,
    input.vdk,
  )
  const vdk = await crypto.subtle.importKey('raw', input.vdk, 'AES-GCM', false, ['encrypt'])
  const blob = await crypto.subtle.encrypt(
    { name: 'AES-GCM', iv: nonceB, additionalData: vaultAad(input.userId, input.vaultVersion) },
    vdk,
    input.plaintext,
  )
  return {
    vaultVersion: input.vaultVersion,
    salt,
    nonceK,
    wrappedVdk: new Uint8Array(wrappedVdk),
    nonceB,
    blob: new Uint8Array(blob),
  }
}

export async function openVaultBytes(
  userId: string,
  payload: VaultPayload,
  recoveryKey: RecoveryKey,
): Promise<OpenedVault> {
  if (
    payload.salt.length !== SALT_BYTES ||
    payload.nonceK.length !== NONCE_BYTES ||
    payload.nonceB.length !== NONCE_BYTES ||
    payload.wrappedVdk.length !== WRAPPED_VDK_BYTES ||
    !Number.isSafeInteger(payload.vaultVersion) ||
    payload.vaultVersion < 1
  ) {
    throw new VaultOpenError('format')
  }
  let vdk: CryptoKey
  try {
    const kek = await deriveKek(recoveryKey, payload.salt, userId, ['unwrapKey'])
    vdk = await crypto.subtle.unwrapKey(
      'raw',
      payload.wrappedVdk,
      kek,
      { name: 'AES-GCM', iv: payload.nonceK, additionalData: kekAad(userId) },
      { name: 'AES-GCM' },
      false,
      ['encrypt', 'decrypt'],
    )
  } catch (error) {
    throw new VaultOpenError('key', { cause: error })
  }
  try {
    const plaintext = await crypto.subtle.decrypt(
      {
        name: 'AES-GCM',
        iv: payload.nonceB,
        additionalData: vaultAad(userId, payload.vaultVersion),
      },
      vdk,
      payload.blob,
    )
    return { vdk, plaintext: new Uint8Array(plaintext) }
  } catch (error) {
    throw new VaultOpenError('blob', { cause: error })
  }
}

async function deriveKek(
  recoveryKey: RecoveryKey,
  salt: Uint8Array<ArrayBuffer>,
  userId: string,
  usages: KeyUsage[],
): Promise<CryptoKey> {
  const bytes = recoveryKeyBytes(recoveryKey)
  try {
    const ikm = await crypto.subtle.importKey('raw', bytes, 'HKDF', false, ['deriveKey'])
    return await crypto.subtle.deriveKey(
      kekParameters(salt, userId),
      ikm,
      { name: 'AES-GCM', length: 256 },
      false,
      usages,
    )
  } finally {
    bytes.fill(0)
  }
}

function freshNonces(): VaultNonces {
  return {
    salt: crypto.getRandomValues(new Uint8Array(SALT_BYTES)),
    nonceK: crypto.getRandomValues(new Uint8Array(NONCE_BYTES)),
    nonceB: crypto.getRandomValues(new Uint8Array(NONCE_BYTES)),
  }
}
