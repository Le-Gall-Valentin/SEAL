import { ApiError, type ErrorCode, NetworkError, retryAfterMinutes } from './apiErrors'

/** Échec d'une requête, tel que l'interface l'affiche (ADR 0024, point 8). */
export type Failure =
  | { readonly kind: 'network' }
  | { readonly kind: 'rate-limited'; readonly minutes: number }
  | { readonly kind: 'refused'; readonly code: ErrorCode }
  | { readonly kind: 'unexpected' }

/** SEC-10 : un 429 sans Retry-After lisible attend au moins une minute. */
const DEFAULT_RETRY_AFTER_SECONDS = 60

export function failureOf(error: unknown): Failure {
  if (error instanceof NetworkError) {
    return { kind: 'network' }
  }
  if (!(error instanceof ApiError) || error.status >= 500 || error.code === 'INTERNAL_ERROR') {
    return { kind: 'unexpected' }
  }
  if (error.code === 'RATE_LIMITED') {
    return {
      kind: 'rate-limited',
      minutes: retryAfterMinutes(error.retryAfterSeconds ?? DEFAULT_RETRY_AFTER_SECONDS),
    }
  }
  return { kind: 'refused', code: error.code }
}
