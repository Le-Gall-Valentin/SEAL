import { describe, expect, it } from 'vitest'
import { formatCountdown, secondsUntil, verifyFailureOf } from './code'
import { ApiError, NetworkError } from '@/shared/lib'

describe('AUTH-14 : compte à rebours « Expire dans »', () => {
  it('AUTH-14 : minutes et secondes, comme la maquette', () => {
    expect(formatCountdown(521)).toBe('8:41')
    expect(formatCountdown(600)).toBe('10:00')
    expect(formatCountdown(9)).toBe('0:09')
    expect(formatCountdown(0)).toBe('0:00')
  })

  it('AUTH-14 : secondes restantes, jamais négatives ; null sans code vivant', () => {
    const now = Date.parse('2026-11-13T20:00:00Z')
    expect(secondsUntil(new Date('2026-11-13T20:08:41Z'), now)).toBe(521)
    expect(secondsUntil(new Date('2026-11-13T20:08:41.400Z'), now)).toBe(522)
    expect(secondsUntil(new Date('2026-11-13T19:59:00Z'), now)).toBe(0)
    expect(secondsUntil(null, now)).toBeNull()
  })
})

describe('AUTH-14, ADR 0024 : refus de la vérification', () => {
  it.each([
    [new ApiError(422, 'INVALID_CODE'), { kind: 'invalid-code' }],
    [new ApiError(422, 'CODE_EXPIRED'), { kind: 'expired' }],
    [
      new ApiError(429, 'RATE_LIMITED', { retryAfterSeconds: 60 }),
      { kind: 'rate-limited', minutes: 1 },
    ],
    [new NetworkError(), { kind: 'network' }],
    [new ApiError(400, 'VALIDATION_FAILED'), { kind: 'unexpected' }],
  ])('AUTH-14, ADR 0024 : %s', (error, expected) => {
    expect(verifyFailureOf(error)).toEqual(expected)
  })
})
