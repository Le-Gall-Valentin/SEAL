import { ApiError, type ErrorCode, NetworkError, retryAfterMinutes } from './apiErrors'

/** Échec d'une requête, tel que l'interface l'affiche (ADR 0024, point 8). */
export type Failure =
  | { readonly kind: 'network' }
  | { readonly kind: 'rate-limited'; readonly minutes: number }
  | { readonly kind: 'refused'; readonly code: ErrorCode }
  | { readonly kind: 'unexpected' }

/** Échecs communs aux formulaires, affichés en alerte au-dessus du formulaire et non sous un champ. */
export type FormFailure = Exclude<Failure, { readonly kind: 'refused' }>

/** L'échec d'un formulaire relève de son alerte : limite atteinte, serveur injoignable, erreur inattendue. */
export function isFormFailure<F extends { readonly kind: string }>(
  failure: F,
): failure is Extract<F, FormFailure> {
  return (
    failure.kind === 'rate-limited' || failure.kind === 'network' || failure.kind === 'unexpected'
  )
}

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
