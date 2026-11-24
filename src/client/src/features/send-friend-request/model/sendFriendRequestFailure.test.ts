import { describe, expect, it } from 'vitest'
import { ApiError, NetworkError } from '@/shared/lib'
import { sendFriendRequestFailureOf } from './sendFriendRequestFailure'

const REQUEST = '0193a1b2-0000-7000-8000-00000000c0de'

describe('features/send-friend-request : issues de 10.1 (ADR 0038, point 1)', () => {
  it.each([
    [404, 'USER_NOT_FOUND', 'not-found'],
    [422, 'CANNOT_INVITE_SELF', 'self'],
    [409, 'ALREADY_FRIENDS', 'already-friends'],
    [409, 'REQUEST_ALREADY_PENDING', 'already-pending'],
    [403, 'CANNOT_INVITE', 'cannot-invite'],
    [400, 'VALIDATION_FAILED', 'invalid-username'],
  ] as const)('FRD-01 : %i %s donne %s', (status, code, kind) => {
    expect(sendFriendRequestFailureOf(new ApiError(status, code))).toEqual({ kind })
  })

  it('FRD-01 : 409 INCOMING_REQUEST_EXISTS garde l’invitation reçue ; sans elle, issue inattendue', () => {
    expect(
      sendFriendRequestFailureOf(
        new ApiError(409, 'INCOMING_REQUEST_EXISTS', { requestId: REQUEST }),
      ),
    ).toEqual({ kind: 'incoming', requestId: REQUEST })
    expect(sendFriendRequestFailureOf(new ApiError(409, 'INCOMING_REQUEST_EXISTS'))).toEqual({
      kind: 'unexpected',
    })
  })

  it('ADR 0038 (point 9) : un requestId sur un autre code ne vaut pas invitation reçue', () => {
    expect(
      sendFriendRequestFailureOf(
        new ApiError(409, 'REQUEST_ALREADY_PENDING', { requestId: REQUEST }),
      ),
    ).toEqual({ kind: 'already-pending' })
  })

  it('SEC-10, A10 : 429, réseau et code imprévu restent des échecs de formulaire', () => {
    expect(
      sendFriendRequestFailureOf(new ApiError(429, 'RATE_LIMITED', { retryAfterSeconds: 300 })),
    ).toEqual({ kind: 'rate-limited', minutes: 5 })
    expect(sendFriendRequestFailureOf(new NetworkError())).toEqual({ kind: 'network' })
    expect(sendFriendRequestFailureOf(new ApiError(403, 'FORBIDDEN'))).toEqual({
      kind: 'unexpected',
    })
  })
})
