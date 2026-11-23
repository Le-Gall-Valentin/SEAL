import { type ErrorCode, failureOf, type FormFailure } from '@/shared/lib'

/** Refus du changement de mot de passe (ACC-01, CDC 14.2). */
export type PasswordChangeFailure =
  { readonly kind: 'invalid-current' } | { readonly kind: 'weak' } | FormFailure

const BY_CODE: ReadonlyMap<ErrorCode, PasswordChangeFailure> = new Map<
  ErrorCode,
  PasswordChangeFailure
>([
  ['INVALID_CREDENTIALS', { kind: 'invalid-current' }],
  ['PASSWORD_TOO_WEAK', { kind: 'weak' }],
])

export function passwordChangeFailureOf(error: unknown): PasswordChangeFailure {
  const failure = failureOf(error)
  return failure.kind === 'refused'
    ? (BY_CODE.get(failure.code) ?? { kind: 'unexpected' })
    : failure
}
