import { type ErrorCode, failureOf, type FormFailure } from '@/shared/lib'

/** Refus de l'invitation, un par ligne du tableau de 10.1 (ADR 0038, point 1). */
export type SendFriendRequestFailure =
  | { readonly kind: 'not-found' }
  | { readonly kind: 'self' }
  | { readonly kind: 'already-friends' }
  | { readonly kind: 'already-pending' }
  | { readonly kind: 'cannot-invite' }
  | { readonly kind: 'invalid-username' }
  | { readonly kind: 'incoming'; readonly requestId: string }
  | FormFailure

const BY_CODE: ReadonlyMap<ErrorCode, SendFriendRequestFailure> = new Map<
  ErrorCode,
  SendFriendRequestFailure
>([
  ['USER_NOT_FOUND', { kind: 'not-found' }],
  ['CANNOT_INVITE_SELF', { kind: 'self' }],
  ['ALREADY_FRIENDS', { kind: 'already-friends' }],
  ['REQUEST_ALREADY_PENDING', { kind: 'already-pending' }],
  // FRD-03 : la même issue pour un blocage et des invitations fermées.
  ['CANNOT_INVITE', { kind: 'cannot-invite' }],
  ['VALIDATION_FAILED', { kind: 'invalid-username' }],
])

export function sendFriendRequestFailureOf(error: unknown): SendFriendRequestFailure {
  const failure = failureOf(error)
  if (failure.kind !== 'refused') {
    return failure
  }
  if (failure.code === 'INCOMING_REQUEST_EXISTS') {
    // ADR 0038 (point 8) : sans l'invitation reçue, rien à accepter ; échec fermé.
    const requestId = error instanceof Object && 'requestId' in error ? error.requestId : null
    return typeof requestId === 'string' ? { kind: 'incoming', requestId } : { kind: 'unexpected' }
  }
  return BY_CODE.get(failure.code) ?? { kind: 'unexpected' }
}
