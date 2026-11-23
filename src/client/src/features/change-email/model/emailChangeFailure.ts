import { type ErrorCode, failureOf, type FormFailure } from '@/shared/lib'

/** Refus de la demande ou de la confirmation du changement de courriel (ACC-02, CDC 14.2 et 14.6). */
export type EmailChangeFailure =
  | { readonly kind: 'reauth-required' }
  | { readonly kind: 'invalid-email' }
  | { readonly kind: 'invalid-code' }
  | { readonly kind: 'expired' }
  | FormFailure

const BY_CODE: ReadonlyMap<ErrorCode, EmailChangeFailure> = new Map<ErrorCode, EmailChangeFailure>([
  // AUTH-55, ADR 0034 : plus de facteur de moins de 10 minutes.
  ['REAUTH_REQUIRED', { kind: 'reauth-required' }],
  // Adresse mal formée, ou l'adresse actuelle.
  ['VALIDATION_FAILED', { kind: 'invalid-email' }],
  ['INVALID_CODE', { kind: 'invalid-code' }],
  ['CODE_EXPIRED', { kind: 'expired' }],
])

export function emailChangeFailureOf(error: unknown): EmailChangeFailure {
  const failure = failureOf(error)
  return failure.kind === 'refused'
    ? (BY_CODE.get(failure.code) ?? { kind: 'unexpected' })
    : failure
}
