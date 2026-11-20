/**
 * Vérification Ed25519 du client (QA-01), interne à shared/crypto : WebCrypto vérifie, après le refus des points
 * d'ordre faible ou non canoniques, comme Ed25519.java côté serveur (PROTOCOL.md, section 4 ; ADR 0025, point 14).
 */

const PUBLIC_KEY_BYTES = 32
const SIGNATURE_BYTES = 64

/** p = 2^255 − 19, le corps de la courbe. */
const FIELD_PRIME = 2n ** 255n - 19n

/**
 * Les y des 8 points d'ordre faible (0, 1, p − 1 et les deux y d'ordre 8), tirés de leurs encodages canoniques, la
 * liste de libsodium. Avec une clé et un R pris parmi ces points, l'équation de vérification tient pour tout message.
 */
const SMALL_ORDER_Y: ReadonlySet<bigint> = new Set(
  [
    '0100000000000000000000000000000000000000000000000000000000000000',
    'ecffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff7f',
    '0000000000000000000000000000000000000000000000000000000000000000',
    '0000000000000000000000000000000000000000000000000000000000000080',
    'c7176a703d4dd84fba3c0b760d10670f2a2053fa2c39ccc64ec7fd7792ac037a',
    'c7176a703d4dd84fba3c0b760d10670f2a2053fa2c39ccc64ec7fd7792ac03fa',
    '26e8958fc2b227b045c3f489f2ef98f0d5dfac05d3c63339b13802886d53fc05',
    '26e8958fc2b227b045c3f489f2ef98f0d5dfac05d3c63339b13802886d53fc85',
  ].map((point) =>
    yOf(
      Uint8Array.from({ length: PUBLIC_KEY_BYTES }, (_, index) =>
        Number.parseInt(point.slice(2 * index, 2 * index + 2), 16),
      ),
    ),
  ),
)

/** y d'un point encodé : 32 octets petit-boutistes, sans le bit de signe de x (RFC 8032, 5.1.3). */
function yOf(encoded: Uint8Array): bigint {
  return encoded
    .subarray(0, PUBLIC_KEY_BYTES)
    .reduceRight(
      (y, byte, index) => (y << 8n) | BigInt(index === PUBLIC_KEY_BYTES - 1 ? byte & 0x7f : byte),
      0n,
    )
}

/**
 * ADR 0025, point 14 : vrai si les 32 premiers octets de `point` sont un point d'ordre faible ou un encodage non
 * canonique, comme le refuse libsodium : bit de signe effacé, y ≥ p, ou y d'un point d'ordre faible.
 */
export function isWeakOrNonCanonical(point: Uint8Array): boolean {
  // Échec fermé : moins de 32 octets ne forment aucun point.
  if (point.length < PUBLIC_KEY_BYTES) {
    return true
  }
  const y = yOf(point)
  return y >= FIELD_PRIME || SMALL_ORDER_Y.has(y)
}

/**
 * Vrai si `signature` est celle de `message` par `publicKey`. Une entrée d'un attaquant (longueur fausse, point d'ordre
 * faible, clé hors de la courbe) donne faux, jamais une exception.
 */
export async function verifyEd25519(
  publicKey: Uint8Array,
  message: Uint8Array,
  signature: Uint8Array,
): Promise<boolean> {
  if (publicKey.length !== PUBLIC_KEY_BYTES || signature.length !== SIGNATURE_BYTES) {
    return false
  }
  if (
    isWeakOrNonCanonical(publicKey) ||
    isWeakOrNonCanonical(signature.subarray(0, PUBLIC_KEY_BYTES))
  ) {
    return false
  }
  try {
    const key = await crypto.subtle.importKey(
      'raw',
      Uint8Array.from(publicKey),
      { name: 'Ed25519' },
      false,
      ['verify'],
    )
    const data = Uint8Array.from(message)
    try {
      return await crypto.subtle.verify({ name: 'Ed25519' }, key, Uint8Array.from(signature), data)
    } finally {
      // PROTOCOL.md 6.2 : la donnée signée peut être un texte clair (SignedPayload) ; sa copie ne survit pas.
      data.fill(0)
    }
  } catch {
    // Clé hors de la courbe : WebCrypto la refuse à l'import.
    return false
  }
}
