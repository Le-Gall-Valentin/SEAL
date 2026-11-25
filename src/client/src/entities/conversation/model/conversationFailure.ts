import { ApiError, type ErrorCode, failureOf, type FormFailure } from '@/shared/lib'

/**
 * Refus d'une action sur une conversation (14.6, ADR 0039), un par texte de l'ADR 0041 (point 4) ; les autres issues
 * reprennent celles des formulaires (réseau, limite, inattendu).
 */
export type ConversationFailure =
  | { readonly kind: 'not-friends' }
  | { readonly kind: 'group-full' }
  | { readonly kind: 'forbidden' }
  | { readonly kind: 'gone' }
  | { readonly kind: 'invalid-name' }
  | FormFailure

const BY_CODE: ReadonlyMap<ErrorCode, ConversationFailure> = new Map<
  ErrorCode,
  ConversationFailure
>([
  // FRD-21 : le même refus pour un inconnu, un non-ami, un ancien ami ou un compte inactif (ADR 0039, point 2).
  ['NOT_FRIENDS', { kind: 'not-friends' }],
  ['GROUP_FULL', { kind: 'group-full' }],
  // GRP-03 : rôle périmé (l'administrateur a changé entre la lecture et l'action).
  ['FORBIDDEN', { kind: 'forbidden' }],
  // CNV-06 : conversation retirée, quittée, ou d'un autre.
  ['NOT_FOUND', { kind: 'gone' }],
])

export function conversationFailureOf(error: unknown): ConversationFailure {
  const failure = failureOf(error)
  if (failure.kind !== 'refused') {
    return failure
  }
  if (failure.code === 'VALIDATION_FAILED') {
    // GRP-01, ADR 0040 : seul le nom peut être refusé d'une saisie que le client a déjà vérifiée.
    return error instanceof ApiError && error.fields.includes('name')
      ? { kind: 'invalid-name' }
      : { kind: 'unexpected' }
  }
  return BY_CODE.get(failure.code) ?? { kind: 'unexpected' }
}
