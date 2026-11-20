/**
 * CDC 7.1, KEY-22 (ADR 0030, point 1) : encapsulation de la CEK pour un destinataire, par HPKE (RFC 9180) en mode
 * base : DHKEM(X25519, HKDF-SHA256), HKDF-SHA256, AES-256-GCM. Le destinataire ouvre avec sa paire : la clé privée
 * non extractable de son trousseau et la clé publique relue. Seul shared/crypto importe @hpke (ESLint).
 */
import { Aes256Gcm, CipherSuite, DhkemX25519HkdfSha256, HkdfSha256 } from '@hpke/core'

const CEK_BYTES = 32

const SUITE = new CipherSuite({
  kem: new DhkemX25519HkdfSha256(),
  kdf: new HkdfSha256(),
  aead: new Aes256Gcm(),
})

/** La CEK ne s'ouvre pas : autre clé, autre info, autre AAD, ou un octet altéré. Rien de plus n'est dit. */
export class HpkeOpenError extends Error {
  override readonly name = 'HpkeOpenError'

  constructor(options?: ErrorOptions) {
    super('encapsulation illisible', options)
  }
}

export interface SealedCek {
  /** Clé publique éphémère de l'émetteur, 32 octets. */
  readonly enc: Uint8Array<ArrayBuffer>
  /** CEK chiffrée et son étiquette, 48 octets. */
  readonly wrappedCek: Uint8Array<ArrayBuffer>
}

export interface CekRecipient {
  readonly privateKey: CryptoKey
  readonly publicKey: Uint8Array
}

/**
 * Scelle la CEK pour la clé publique X25519 du destinataire. `ephemeral` fixe la clé éphémère : réservé aux vecteurs
 * (interne à shared/crypto, jamais exporté de l'index).
 */
export async function sealCek(
  recipientPublicKey: Uint8Array,
  info: Uint8Array,
  aad: Uint8Array,
  cek: Uint8Array,
  ephemeral?: Uint8Array,
): Promise<SealedCek> {
  if (cek.length !== CEK_BYTES) {
    throw new RangeError('la CEK fait 32 octets')
  }
  const { ct, enc } = await SUITE.seal(
    {
      recipientPublicKey: await importPublic(recipientPublicKey),
      info,
      ...(ephemeral === undefined ? {} : { ekm: ephemeral }),
    },
    cek,
    aad,
  )
  return { enc: new Uint8Array(enc), wrappedCek: new Uint8Array(ct) }
}

/** Ouvre la CEK avec la paire du destinataire ; tout échec lève HpkeOpenError. */
export async function openCek(
  recipient: CekRecipient,
  enc: Uint8Array,
  wrappedCek: Uint8Array,
  info: Uint8Array,
  aad: Uint8Array,
): Promise<Uint8Array<ArrayBuffer>> {
  try {
    const publicKey = await importPublic(recipient.publicKey)
    const cek = new Uint8Array(
      await SUITE.open(
        { recipientKey: { privateKey: recipient.privateKey, publicKey }, enc, info },
        wrappedCek,
        aad,
      ),
    )
    if (cek.length !== CEK_BYTES) {
      cek.fill(0)
      throw new HpkeOpenError()
    }
    return cek
  } catch (error) {
    throw error instanceof HpkeOpenError ? error : new HpkeOpenError({ cause: error })
  }
}

/** Clé publique X25519 brute, copiée dans un ArrayBuffer à sa taille exacte. */
async function importPublic(publicKey: Uint8Array): Promise<CryptoKey> {
  return SUITE.kem.importKey('raw', Uint8Array.from(publicKey).buffer, true)
}
