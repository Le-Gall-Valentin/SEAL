import { type ErrorCode, failureOf } from '@/shared/lib'

/** Secondes avant l'expiration du code vivant, arrondies au-dessus ; null sans code vivant (ADR 0019). */
export function secondsUntil(expiresAt: Date | null, now: number): number | null {
  return expiresAt === null ? null : Math.max(0, Math.ceil((expiresAt.getTime() - now) / 1000))
}

/** « 8:41 », comme Verification-Courriel. */
export function formatCountdown(seconds: number): string {
  const minutes = Math.floor(seconds / 60)
  return `${String(minutes)}:${String(seconds % 60).padStart(2, '0')}`
}

/** Refus de la vérification, du renvoi ou du changement de courriel (ADR 0024, point 9). */
export type VerifyFailure =
  | { readonly kind: 'invalid-code' }
  | { readonly kind: 'expired' }
  | { readonly kind: 'rate-limited'; readonly minutes: number }
  | { readonly kind: 'network' }
  | { readonly kind: 'unexpected' }

const BY_CODE: ReadonlyMap<ErrorCode, VerifyFailure> = new Map<ErrorCode, VerifyFailure>([
  ['INVALID_CODE', { kind: 'invalid-code' }],
  ['CODE_EXPIRED', { kind: 'expired' }],
])

export function verifyFailureOf(error: unknown): VerifyFailure {
  const failure = failureOf(error)
  return failure.kind === 'refused'
    ? (BY_CODE.get(failure.code) ?? { kind: 'unexpected' })
    : failure
}
