/**
 * CDC 7.1, KEY-22 (ADR 0030, point 1) : encapsulation de la CEK pour un destinataire, par HPKE (RFC 9180) en mode
 * base : DHKEM(X25519, HKDF-SHA256), HKDF-SHA256, AES-256-GCM. Le destinataire ouvre avec sa paire : la clé privée
 * non extractable de son trousseau et la clé publique relue. Seul shared/crypto importe @hpke (ESLint).
 */
import { Aes256Gcm, CipherSuite, DhkemX25519HkdfSha256, HkdfSha256 } from '@hpke/core'
import { CEK_BYTES } from './protocol'

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

/** RFC 9180 (7.1.4) : la clé publique du destinataire est hors de la courbe ou d'ordre faible ; rien n'est scellé. */
export class HpkeSealError extends Error {
  override readonly name = 'HpkeSealError'

  constructor(options?: ErrorOptions) {
    super('clé publique du destinataire inutilisable', options)
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
 * Scelle la CEK pour la clé publique X25519 du destinataire ; une clé inutilisable lève HpkeSealError. `ephemeral`
 * fixe la clé éphémère : réservé aux vecteurs (interne à shared/crypto, jamais exporté de l'index).
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
  // PROTOCOL.md 6.2 (ADR 0030, point 9) : @hpke recopie une vue, jamais un ArrayBuffer exact. La CEK lui est confiée
  // dans une copie à soi, effacée une fois scellée ; celle de l'appelant reste à lui.
  const exact = Uint8Array.from(cek)
  let sealed
  try {
    sealed = await SUITE.seal(
      {
        recipientPublicKey: await importPublic(recipientPublicKey),
        info,
        ...(ephemeral === undefined ? {} : { ekm: ephemeral }),
      },
      exact.buffer,
      aad,
    )
  } catch (error) {
    // WebCrypto refuse un secret DH nul : une clé d'ordre faible ne donne jamais une CEK lisible par tous.
    throw new HpkeSealError({ cause: error })
  } finally {
    exact.fill(0)
  }
  return { enc: new Uint8Array(sealed.enc), wrappedCek: new Uint8Array(sealed.ct) }
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
