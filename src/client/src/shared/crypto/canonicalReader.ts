/**
 * Relecture d'une structure écrite par canonical.ts (PROTOCOL.md, section 2 ; MSG-01), champ par champ et dans l'ordre,
 * comme `CanonicalReader` côté serveur. Toute valeur hors du format lève CanonicalEncodingError : longueur au-delà des
 * octets restants, u64 au-delà de 2^63 - 1, UTF-8 invalide ou hors de la forme NFC, autre étiquette, octet en trop
 * après `end()`.
 */
import { CanonicalEncodingError, U64_MAX, uuidText } from './canonical'

const UUID_BYTES = 16
/** UTF-8 strict ; un BOM en tête est gardé (U+FEFF), comme le décodeur de Java, au lieu d'être retiré. */
const UTF8 = new TextDecoder('utf-8', { fatal: true, ignoreBOM: true })

export class CanonicalReader {
  readonly #bytes: Uint8Array
  readonly #view: DataView
  #offset = 0

  /**
   * Lit le tampon de l'appelant, sans le recopier : sa remise à zéro (PROTOCOL.md 6.2) couvre aussi le lecteur. Les
   * lectures sont synchrones ; `bytes()` rend des copies.
   */
  private constructor(encoded: Uint8Array) {
    this.#bytes = encoded
    this.#view = new DataView(encoded.buffer, encoded.byteOffset, encoded.byteLength)
  }

  /** Une suite de champs sans étiquette, comme `Plaintext` d'un message (CDC 7.3). */
  static unlabelled(encoded: Uint8Array): CanonicalReader {
    return new CanonicalReader(encoded)
  }

  /** Ouvre la structure `struct(label, …)` et vérifie son étiquette. */
  static of(encoded: Uint8Array, label: string): CanonicalReader {
    const reader = new CanonicalReader(encoded)
    if (reader.text() !== label) {
      throw new CanonicalEncodingError('struct : étiquette inattendue')
    }
    return reader
  }

  u8(): number {
    const value = this.#view.getUint8(this.#take(1))
    return value
  }

  u32(): number {
    return this.#view.getUint32(this.#take(4))
  }

  u64(): bigint {
    const value = this.#view.getBigUint64(this.#take(8))
    if (value > U64_MAX) {
      throw new CanonicalEncodingError('u64 au-delà de 2^63 - 1')
    }
    return value
  }

  /** Un u64 qui doit tenir dans un `number` (au plus 2^53 - 1) : millisecondes, compteurs. */
  safeU64(): number {
    const value = this.u64()
    if (value > BigInt(Number.MAX_SAFE_INTEGER)) {
      throw new CanonicalEncodingError('u64 au-delà de 2^53 - 1')
    }
    return Number(value)
  }

  bytes(): Uint8Array<ArrayBuffer> {
    return Uint8Array.from(this.#field())
  }

  /** PROTOCOL.md 2.1, ADR 0006 : UTF-8 strict, en forme NFC, comme l'écrit `text()`. */
  text(): string {
    let value: string
    try {
      value = UTF8.decode(this.#field())
    } catch {
      throw new CanonicalEncodingError('text : UTF-8 invalide')
    }
    if (value.normalize('NFC') !== value) {
      throw new CanonicalEncodingError('text : hors de la forme NFC')
    }
    return value
  }

  /** 16 octets dans l'ordre réseau, rendus sous la forme 8-4-4-4-12 en minuscules. */
  uuid(): string {
    const start = this.#take(UUID_BYTES)
    return uuidText(this.#bytes.subarray(start, start + UUID_BYTES))
  }

  /**
   * `list(T, xs)` : `u32(n)` puis n éléments lus par `element`. Un élément fait au moins un octet : un `n` au-delà des
   * octets restants est refusé avant toute lecture.
   */
  list<T>(element: () => T): T[] {
    const count = this.u32()
    if (count > this.#bytes.length - this.#offset) {
      throw new CanonicalEncodingError('list : plus d’éléments que d’octets restants')
    }
    return Array.from({ length: count }, () => element())
  }

  /** Exige qu'aucun octet ne suive le dernier champ. */
  end(): void {
    if (this.#offset !== this.#bytes.length) {
      throw new CanonicalEncodingError('octets en trop après la structure')
    }
  }

  /** Les octets de `bytes(…)`, en vue sur le tampon lu. */
  #field(): Uint8Array {
    const length = this.u32()
    const start = this.#take(length)
    return this.#bytes.subarray(start, start + length)
  }

  #take(length: number): number {
    if (length > this.#bytes.length - this.#offset) {
      throw new CanonicalEncodingError('structure tronquée')
    }
    const start = this.#offset
    this.#offset += length
    return start
  }
}
