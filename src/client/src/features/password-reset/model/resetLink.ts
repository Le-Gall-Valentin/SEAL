import { ApiError, type ErrorCode, failureOf } from '@/shared/lib'

/** ADR 0022, point 2 : « #jeton= » suivi de 43 caractères base64url (32 octets), seul dans le fragment. */
const TOKEN_FRAGMENT = /^#jeton=([A-Za-z0-9_-]{43})$/

/** Jeton du lien, ou null si le fragment ne suit pas la forme de l'ADR 0022. */
export function readResetToken(hash: string): string | null {
  return TOKEN_FRAGMENT.exec(hash)?.[1] ?? null
}

/** « Ce lien expire dans N minutes » : arrondi au-dessus, une minute au moins. */
export function minutesLeft(expiresAt: Date, now: number): number {
  return Math.max(1, Math.ceil((expiresAt.getTime() - now) / 60_000))
}

/** Refus de la demande, de la lecture ou de la confirmation (ADR 0022 ; ADR 0024, point 9). */
export type ResetFailure =
  | { readonly kind: 'expired-link' }
  | { readonly kind: 'password' }
  | { readonly kind: 'email' }
  | { readonly kind: 'rate-limited'; readonly minutes: number }
  | { readonly kind: 'network' }
  | { readonly kind: 'unexpected' }

const BY_CODE: ReadonlyMap<ErrorCode, ResetFailure> = new Map<ErrorCode, ResetFailure>([
  ['CODE_EXPIRED', { kind: 'expired-link' }],
  ['PASSWORD_TOO_WEAK', { kind: 'password' }],
])

function fieldFailure(error: unknown): ResetFailure {
  if (!(error instanceof ApiError)) {
    return { kind: 'unexpected' }
  }
  if (error.fields.includes('token')) {
    return { kind: 'expired-link' }
  }
  return error.fields.includes('email') ? { kind: 'email' } : { kind: 'unexpected' }
}

export function resetFailureOf(error: unknown): ResetFailure {
  const failure = failureOf(error)
  if (failure.kind !== 'refused') {
    return failure
  }
  return failure.code === 'VALIDATION_FAILED'
    ? fieldFailure(error)
    : (BY_CODE.get(failure.code) ?? { kind: 'unexpected' })
}
