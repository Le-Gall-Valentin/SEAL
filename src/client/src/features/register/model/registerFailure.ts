import { ApiError, type ErrorCode, failureOf } from '@/shared/lib'

/** Refus de l'inscription, rattaché à son champ quand il en a un (ADR 0024, point 9). */
export type RegisterFailure =
  | { readonly kind: 'username'; readonly reason: 'taken' | 'invalid' | 'reserved' }
  | { readonly kind: 'email' }
  | { readonly kind: 'displayName' }
  | { readonly kind: 'password' }
  | { readonly kind: 'rate-limited'; readonly minutes: number }
  | { readonly kind: 'network' }
  | { readonly kind: 'unexpected' }

/** Refus attendus de POST /api/v1/auth/register (CDC, section 14.6 ; ADR 0019) ; tout autre est inattendu. */
const BY_CODE: ReadonlyMap<ErrorCode, RegisterFailure> = new Map<ErrorCode, RegisterFailure>([
  ['USERNAME_TAKEN', { kind: 'username', reason: 'taken' }],
  ['USERNAME_INVALID', { kind: 'username', reason: 'invalid' }],
  ['USERNAME_RESERVED', { kind: 'username', reason: 'reserved' }],
  ['PASSWORD_TOO_WEAK', { kind: 'password' }],
])

/** VALIDATION_FAILED : champ en erreur, sans sa valeur (ADR 0010). */
function fieldFailure(fields: readonly string[]): RegisterFailure {
  if (fields.includes('email')) {
    return { kind: 'email' }
  }
  if (fields.includes('displayName')) {
    return { kind: 'displayName' }
  }
  if (fields.includes('username')) {
    return { kind: 'username', reason: 'invalid' }
  }
  return { kind: 'unexpected' }
}

export function registerFailureOf(error: unknown): RegisterFailure {
  const failure = failureOf(error)
  if (failure.kind !== 'refused') {
    return failure
  }
  if (failure.code === 'VALIDATION_FAILED') {
    return error instanceof ApiError ? fieldFailure(error.fields) : { kind: 'unexpected' }
  }
  return BY_CODE.get(failure.code) ?? { kind: 'unexpected' }
}
