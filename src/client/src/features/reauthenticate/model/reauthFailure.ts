import { failureOf, type FormFailure } from '@/shared/lib'

/** Refus de la ré-authentification par mot de passe (AUTH-55, ADR 0035). */
export type ReauthFailure = { readonly kind: 'invalid-password' } | FormFailure

export function reauthFailureOf(error: unknown): ReauthFailure {
  const failure = failureOf(error)
  if (failure.kind !== 'refused') {
    return failure
  }
  // ADR 0034 : un mauvais mot de passe, ou un compte sans mot de passe, répond INVALID_CREDENTIALS.
  return failure.code === 'INVALID_CREDENTIALS'
    ? { kind: 'invalid-password' }
    : { kind: 'unexpected' }
}
