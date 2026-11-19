/**
 * Coffre de clés (CDC, section 6.4 ; PROTOCOL.md, section 5 ; KEY-31, ADR 0027). La VDK chiffre le trousseau, la KEK
 * tirée de RK par HKDF chiffre la VDK ; chaque AAD attache le niveau au compte, et le blob à sa version. À l'ouverture,
 * la VDK sort d'unwrapKey déjà non extractable (KEY-40) ; toute erreur devient VaultOpenError, sans résultat partiel.
 */
import { fromBase64Url, toBase64Url } from './base64url'
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

/** Coffre tel que l'API l'échange (GET et PUT /api/v1/keys/vault) : octets en base64url (ADR 0029). */
export interface VaultWire {
  readonly vaultVersion: number
  readonly salt: string
  readonly nonceK: string
  readonly wrappedVdk: string
  readonly nonceB: string
  readonly blob: string
}

export function vaultToWire(payload: VaultPayload): VaultWire {
  return {
    vaultVersion: payload.vaultVersion,
    salt: toBase64Url(payload.salt),
    nonceK: toBase64Url(payload.nonceK),
    wrappedVdk: toBase64Url(payload.wrappedVdk),
    nonceB: toBase64Url(payload.nonceB),
    blob: toBase64Url(payload.blob),
  }
}

/** Un base64url non canonique refuse le coffre à l'étage 'format' ; les tailles se contrôlent à l'ouverture. */
export function vaultFromWire(wire: VaultWire): VaultPayload {
  try {
    return {
      vaultVersion: wire.vaultVersion,
      salt: fromBase64Url(wire.salt),
      nonceK: fromBase64Url(wire.nonceK),
      wrappedVdk: fromBase64Url(wire.wrappedVdk),
      nonceB: fromBase64Url(wire.nonceB),
      blob: fromBase64Url(wire.blob),
    }
  } catch (error) {
    throw new VaultOpenError('format', { cause: error })
  }
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
  return {
    vaultVersion: input.vaultVersion,
    salt,
    nonceK,
    wrappedVdk: new Uint8Array(wrappedVdk),
    nonceB,
    blob: await encryptBlob(input.userId, input.vaultVersion, vdk, input.plaintext, nonceB),
  }
}

export async function openVaultBytes(
  userId: string,
  payload: VaultPayload,
  recoveryKey: RecoveryKey,
): Promise<OpenedVault> {
  checkFormat(payload)
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
  return { vdk, plaintext: await decryptBlob(userId, payload, vdk) }
}

/** PROTOCOL.md 5.3 : le trousseau chiffré par la VDK, sous l'AAD de sa version. */
export async function encryptBlob(
  userId: string,
  vaultVersion: number,
  vdk: CryptoKey,
  plaintext: Uint8Array<ArrayBuffer>,
  nonceB: Uint8Array<ArrayBuffer>,
): Promise<Uint8Array<ArrayBuffer>> {
  const blob = await crypto.subtle.encrypt(
    { name: 'AES-GCM', iv: nonceB, additionalData: vaultAad(userId, vaultVersion) },
    vdk,
    plaintext,
  )
  return new Uint8Array(blob)
}

/**
 * KEY-32, KEY-33 : le trousseau en clair, déchiffré par la VDK ; tailles et version d'abord ('format'), puis
 * l'étiquette GCM, qui lie le blob au compte et à sa version ('blob').
 */
export async function decryptBlob(
  userId: string,
  payload: VaultPayload,
  vdk: CryptoKey,
): Promise<Uint8Array<ArrayBuffer>> {
  checkFormat(payload)
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
    return new Uint8Array(plaintext)
  } catch (error) {
    throw new VaultOpenError('blob', { cause: error })
  }
}

/**
 * KEY-32, PROTOCOL.md 5.5 : version suivante du coffre, rechiffrée par la même VDK sous un nonceB neuf ; sel, nonceK
 * et VDK enveloppée ne changent pas, et la clé de récupération n'est pas nécessaire.
 */
export async function sealNextVersion(
  userId: string,
  payload: VaultPayload,
  vdk: CryptoKey,
  plaintext: Uint8Array<ArrayBuffer>,
): Promise<VaultPayload> {
  checkFormat(payload)
  const vaultVersion = payload.vaultVersion + 1
  if (!Number.isSafeInteger(vaultVersion)) {
    throw new VaultOpenError('format')
  }
  const nonceB = crypto.getRandomValues(new Uint8Array(NONCE_BYTES))
  return {
    vaultVersion,
    salt: Uint8Array.from(payload.salt),
    nonceK: Uint8Array.from(payload.nonceK),
    wrappedVdk: Uint8Array.from(payload.wrappedVdk),
    nonceB,
    blob: await encryptBlob(userId, vaultVersion, vdk, plaintext, nonceB),
  }
}

/** PROTOCOL.md 5.3 : une taille fausse ou une version impossible sont refusées avant tout calcul. */
function checkFormat(payload: VaultPayload): void {
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
