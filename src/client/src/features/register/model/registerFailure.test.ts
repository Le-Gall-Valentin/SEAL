import { describe, expect, it } from 'vitest'
import { ApiError, NetworkError } from '@/shared/lib'
import { registerFailureOf } from './registerFailure'

describe('features/register : refus du serveur à l’inscription', () => {
  it.each([
    [new ApiError(409, 'USERNAME_TAKEN'), { kind: 'username', reason: 'taken' }],
    [new ApiError(400, 'USERNAME_INVALID'), { kind: 'username', reason: 'invalid' }],
    [new ApiError(422, 'USERNAME_RESERVED'), { kind: 'username', reason: 'reserved' }],
    [new ApiError(422, 'PASSWORD_TOO_WEAK'), { kind: 'password' }],
    [new ApiError(400, 'VALIDATION_FAILED', { fields: ['email'] }), { kind: 'email' }],
    [new ApiError(400, 'VALIDATION_FAILED', { fields: ['displayName'] }), { kind: 'displayName' }],
    [
      new ApiError(400, 'VALIDATION_FAILED', { fields: ['username'] }),
      { kind: 'username', reason: 'invalid' },
    ],
    [
      new ApiError(429, 'RATE_LIMITED', { retryAfterSeconds: 3600 }),
      { kind: 'rate-limited', minutes: 60 },
    ],
    [new NetworkError(), { kind: 'network' }],
    [new ApiError(403, 'FORBIDDEN'), { kind: 'unexpected' }],
    [new ApiError(400, 'VALIDATION_FAILED', { fields: ['inconnu'] }), { kind: 'unexpected' }],
  ])('AUTH-10, ADR 0024 : %s', (error, expected) => {
    expect(registerFailureOf(error)).toEqual(expected)
  })
})
