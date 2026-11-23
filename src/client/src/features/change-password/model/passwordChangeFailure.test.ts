import { describe, expect, it } from 'vitest'
import { ApiError, NetworkError } from '@/shared/lib'
import { passwordChangeFailureOf } from './passwordChangeFailure'

describe('ACC-01 : refus du changement de mot de passe', () => {
  it.each([
    [new ApiError(401, 'INVALID_CREDENTIALS'), { kind: 'invalid-current' }],
    [new ApiError(422, 'PASSWORD_TOO_WEAK'), { kind: 'weak' }],
    [
      new ApiError(429, 'RATE_LIMITED', { retryAfterSeconds: 900 }),
      { kind: 'rate-limited', minutes: 15 },
    ],
    [new NetworkError(), { kind: 'network' }],
    [new ApiError(400, 'VALIDATION_FAILED'), { kind: 'unexpected' }],
  ])('ACC-01 : %s', (error, expected) => {
    expect(passwordChangeFailureOf(error)).toEqual(expected)
  })
})
