import { describe, expect, it } from 'vitest'
import { ApiError, NetworkError } from '@/shared/lib'
import { emailChangeFailureOf } from './emailChangeFailure'

describe('ACC-02 : refus du changement de courriel', () => {
  it.each([
    [new ApiError(403, 'REAUTH_REQUIRED'), { kind: 'reauth-required' }],
    [new ApiError(400, 'VALIDATION_FAILED'), { kind: 'invalid-email' }],
    [new ApiError(422, 'INVALID_CODE'), { kind: 'invalid-code' }],
    [new ApiError(422, 'CODE_EXPIRED'), { kind: 'expired' }],
    [
      new ApiError(429, 'RATE_LIMITED', { retryAfterSeconds: 3600 }),
      { kind: 'rate-limited', minutes: 60 },
    ],
    [new NetworkError(), { kind: 'network' }],
    [new ApiError(403, 'FORBIDDEN'), { kind: 'unexpected' }],
  ])('ACC-02 : %s', (error, expected) => {
    expect(emailChangeFailureOf(error)).toEqual(expected)
  })
})
