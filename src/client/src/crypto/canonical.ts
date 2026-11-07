/**
 * Encodage canonique des structures signées ou hachées (CDC section 7.2, PROTOCOL.md section 2 ;
 * MSG-01).
 *
 * Chaque fonction reprend une primitive de la grammaire et renvoie ses octets, si bien qu'une
 * structure s'écrit comme dans la spécification :
 * `struct('SEAL-aad-v1', u8(1), text(suite), uuid(messageId), …)`. Une valeur hors domaine lève
 * `CanonicalEncodingError` (ADR 0006) ; jamais `JSON.stringify` pour produire des octets signés.
 */

/** Valeur hors du domaine de l'encodage canonique : elle est rejetée, jamais encodée. */
export class CanonicalEncodingError extends Error {
  override readonly name = 'CanonicalEncodingError'
}

const U32_MAX = 0xffff_ffff
/** ADR 0006 : les u64 s'arrêtent à 2^63-1, comme le long de Java. */
const U64_MAX = 2n ** 63n - 1n
/** ADR 0006 : forme 8-4-4-4-12 exactement, majuscules ou minuscules. */
const UUID_FORM = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i
const UTF8 = new TextEncoder()

function integer(value: number, max: number, kind: string): number {
  if (!Number.isInteger(value) || value < 0 || value > max) {
    throw new CanonicalEncodingError(`${kind} hors de 0..${String(max)} : ${String(value)}`)
  }
  return value
}

/** `u8(x)` : 1 octet, de 0 à 255. */
export function u8(value: number): Uint8Array<ArrayBuffer> {
  return Uint8Array.of(integer(value, 0xff, 'u8'))
}

/** `u32(x)` : 4 octets gros-boutistes, de 0 à 2^32-1. */
export function u32(value: number): Uint8Array<ArrayBuffer> {
  const out = new Uint8Array(4)
  new DataView(out.buffer).setUint32(0, integer(value, U32_MAX, 'u32'))
  return out
}

/**
 * `u64(x)` : 8 octets gros-boutistes, de 0 à 2^63-1 (ADR 0006). Un `number` doit être un entier
 * sûr (au plus 2^53-1) ; au-delà, passer un `bigint`.
 */
export function u64(value: bigint | number): Uint8Array<ArrayBuffer> {
  if (typeof value === 'number' && !Number.isSafeInteger(value)) {
    throw new CanonicalEncodingError(
      `u64 : number non entier ou au-delà de 2^53-1 : ${String(value)}`,
    )
  }
  const big = BigInt(value)
  if (big < 0n || big > U64_MAX) {
    throw new CanonicalEncodingError(`u64 hors de 0..2^63-1 : ${String(big)}`)
  }
  const out = new Uint8Array(8)
  new DataView(out.buffer).setBigUint64(0, big)
  return out
}

/** `bytes(b)` : `u32(longueur de b) || b`. */
export function bytes(value: Uint8Array): Uint8Array<ArrayBuffer> {
  return concat(u32(value.length), value)
}

/**
 * `text(s)` : `bytes(UTF-8(NFC(s)))`. Un demi-caractère Unicode isolé est rejeté : TextEncoder le
 * remplacerait en silence par U+FFFD (ADR 0006).
 */
export function text(value: string): Uint8Array<ArrayBuffer> {
  if (!value.isWellFormed()) {
    throw new CanonicalEncodingError('text : demi-caractère Unicode isolé')
  }
  return bytes(UTF8.encode(value.normalize('NFC')))
}

/** `uuid(u)` : 16 octets dans l'ordre réseau, depuis la forme textuelle 8-4-4-4-12 (ADR 0006). */
export function uuid(value: string): Uint8Array<ArrayBuffer> {
  if (!UUID_FORM.test(value)) {
    throw new CanonicalEncodingError('uuid hors de la forme 8-4-4-4-12')
  }
  const digits = value.replaceAll('-', '')
  const out = new Uint8Array(16)
  for (let i = 0; i < out.length; i++) {
    out[i] = Number.parseInt(digits.slice(2 * i, 2 * i + 2), 16)
  }
  return out
}

/**
 * `list(T, xs)` : `u32(nombre d'éléments) || T(x1) || … || T(xn)`. Le tri éventuel des éléments
 * revient à la structure qui appelle.
 */
export function list<T>(
  element: (item: T) => Uint8Array,
  items: readonly T[],
): Uint8Array<ArrayBuffer> {
  return concat(u32(items.length), ...items.map(element))
}

/** `a || b || …` : concaténation, pour les éléments composés d'une liste ou le texte clair d'un message. */
export function concat(...parts: readonly Uint8Array[]): Uint8Array<ArrayBuffer> {
  const out = new Uint8Array(parts.reduce((length, part) => length + part.length, 0))
  let offset = 0
  for (const part of parts) {
    out.set(part, offset)
    offset += part.length
  }
  return out
}

/**
 * `struct(label, f1, …, fn)` : `text(label) || f1 || … || fn`. L'étiquette, non vide, sépare les
 * domaines : une signature d'un type de structure ne vaut pas pour un autre.
 */
export function struct(label: string, ...fields: readonly Uint8Array[]): Uint8Array<ArrayBuffer> {
  if (label === '') {
    throw new CanonicalEncodingError('struct : étiquette vide')
  }
  return concat(text(label), ...fields)
}
