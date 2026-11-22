import { describe, expect, it } from 'vitest'
import { ApiError, NetworkError } from '@/shared/lib'
import { reauthFailureOf } from './reauthFailure'

describe('AUTH-55, ADR 0035 : refus de la ré-authentification', () => {
  it.each([
    [new ApiError(401, 'INVALID_CREDENTIALS'), { kind: 'invalid-password' }],
    [
      new ApiError(429, 'RATE_LIMITED', { retryAfterSeconds: 120 }),
      { kind: 'rate-limited', minutes: 2 },
    ],
    [new NetworkError(), { kind: 'network' }],
    [new ApiError(403, 'FORBIDDEN'), { kind: 'unexpected' }],
    [new ApiError(500, 'INTERNAL_ERROR'), { kind: 'unexpected' }],
  ])('AUTH-55 : %s', (error, expected) => {
    expect(reauthFailureOf(error)).toEqual(expected)
  })
})
