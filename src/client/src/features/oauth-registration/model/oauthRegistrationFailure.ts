import { refusedAccountField, usernameRefusalReason } from '@/entities/account'
import type { OAuthLoginError } from '@/entities/oauth-provider'
import { ApiError, type ErrorCode, failureOf } from '@/shared/lib'

/** Refus de POST /api/v1/auth/oauth/complete, rattaché à son champ, ou abandon de l'inscription (ADR 0033). */
export type OAuthRegistrationFailure =
  | { readonly kind: 'username'; readonly reason: 'taken' | 'invalid' | 'reserved' }
  | { readonly kind: 'displayName' }
  /** ADR 0033 (point 2) : l'inscription ne peut plus aboutir ; retour à la connexion avec ce code. */
  | { readonly kind: 'abandoned'; readonly error: OAuthLoginError }
  | { readonly kind: 'rate-limited'; readonly minutes: number }
  | { readonly kind: 'network' }
  | { readonly kind: 'unexpected' }

/**
 * ADR 0033 (point 2) : refus qui empêchent l'inscription d'aboutir. Attente expirée ou consommée, ou identité liée
 * entre-temps : le bouton du fournisseur la reprend. Courriel activé par un autre compte entre-temps : AUTH-33.
 */
const ABANDONS: ReadonlyMap<ErrorCode, OAuthLoginError> = new Map<ErrorCode, OAuthLoginError>([
  ['NOT_FOUND', 'REQUEST_REJECTED'],
  ['OAUTH_ALREADY_LINKED', 'REQUEST_REJECTED'],
  ['OAUTH_EMAIL_IN_USE', 'OAUTH_EMAIL_IN_USE'],
])

/** VALIDATION_FAILED : champ en erreur, sans sa valeur (ADR 0010) ; la fin d'inscription n'a pas de courriel. */
function fieldFailure(fields: readonly string[]): OAuthRegistrationFailure {
  const field = refusedAccountField(fields)
  switch (field) {
    case 'displayName':
      return { kind: 'displayName' }
    case 'username':
      return { kind: 'username', reason: 'invalid' }
    case 'email':
    case null:
      return { kind: 'unexpected' }
  }
}

export function oauthRegistrationFailureOf(error: unknown): OAuthRegistrationFailure {
  const failure = failureOf(error)
  if (failure.kind !== 'refused') {
    return failure
  }
  if (failure.code === 'VALIDATION_FAILED') {
    return error instanceof ApiError ? fieldFailure(error.fields) : { kind: 'unexpected' }
  }
  const abandon = ABANDONS.get(failure.code)
  if (abandon !== undefined) {
    return { kind: 'abandoned', error: abandon }
  }
  const reason = usernameRefusalReason(failure.code)
  return reason === null ? { kind: 'unexpected' } : { kind: 'username', reason }
}
