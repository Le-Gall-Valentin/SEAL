/**
 * Lecteur commun des vecteurs de src/protocol/vectors (QA-01, QA-02), réservé aux tests. Chaque accès vérifie la forme
 * attendue plutôt que de supposer ce que rend JSON.parse. Sans API de Node, il sert aussi aux tests dans Chromium ;
 * chaque test lit lui-même son fichier.
 */
import { fromBase64Url } from '../base64url'

export type Json = Record<string, unknown>

const DECIMAL = /^(0|[1-9][0-9]*)$/
const HEX = /^(?:[0-9a-fA-F]{2})*$/

export function isRecord(value: unknown): value is Json {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

export function record(value: unknown): Json {
  if (!isRecord(value)) throw new Error('objet attendu')
  return value
}

export function records(value: unknown): Json[] {
  if (!Array.isArray(value)) throw new Error('liste attendue')
  return value.map(record)
}

export function string(json: Json, field: string): string {
  const value = json[field]
  if (typeof value !== 'string') throw new Error(`${field} : chaîne attendue`)
  return value
}

export function number(json: Json, field: string): number {
  const value = json[field]
  if (typeof value !== 'number') throw new Error(`${field} : nombre attendu`)
  return value
}

/** ADR 0006, PROTOCOL.md 2.5 : un u64 est une chaîne décimale, que JSON.parse ne peut arrondir. */
export function decimal(json: Json, field: string): number {
  const value = string(json, field)
  if (!DECIMAL.test(value)) throw new Error(`${field} : u64 décimal attendu`)
  const parsed = Number(value)
  if (!Number.isSafeInteger(parsed)) throw new Error(`${field} : u64 hors des entiers sûrs`)
  return parsed
}

/** PROTOCOL.md 1 : octets en base64url sans remplissage, décodés par le décodeur strict du segment. */
export function base64url(json: Json, field: string): Uint8Array<ArrayBuffer> {
  const value = string(json, field)
  try {
    return fromBase64Url(value)
  } catch (error) {
    throw new Error(`${field} : base64url sans remplissage attendu`, { cause: error })
  }
}

export function hex(json: Json, field: string): Uint8Array<ArrayBuffer> {
  const value = string(json, field)
  if (!HEX.test(value)) throw new Error(`${field} : hexadécimal attendu`)
  return Uint8Array.from(value.match(/../g) ?? [], (pair) => Number.parseInt(pair, 16))
}
