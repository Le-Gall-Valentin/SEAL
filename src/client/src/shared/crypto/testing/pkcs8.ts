/** RFC 8410 : une clé privée brute de 32 octets, enveloppée en PKCS#8 pour l'importer dans WebCrypto. Réservé aux tests. */
import { concat } from '../canonical'

/** En-tête PKCS#8 commun, l'OID de l'algorithme (1.3.101.110 pour X25519, 1.3.101.112 pour Ed25519) mis à part. */
function prefix(oidLastByte: number): Uint8Array {
  return Uint8Array.of(
    0x30,
    0x2e,
    0x02,
    0x01,
    0x00,
    0x30,
    0x05,
    0x06,
    0x03,
    0x2b,
    0x65,
    oidLastByte,
    0x04,
    0x22,
    0x04,
    0x20,
  )
}

/** La clé privée X25519 `raw` (RFC 7748), en PKCS#8. */
export function x25519Pkcs8(raw: Uint8Array): Uint8Array<ArrayBuffer> {
  return concat(prefix(0x6e), raw)
}

/** La graine Ed25519 `seed` (RFC 8032), en PKCS#8. */
export function ed25519Pkcs8(seed: Uint8Array): Uint8Array<ArrayBuffer> {
  return concat(prefix(0x70), seed)
}
