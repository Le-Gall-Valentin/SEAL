import { describe, expect, it } from 'vitest'
import { ApiError, NetworkError } from '@/shared/lib'
import { oauthRegistrationFailureOf } from './oauthRegistrationFailure'

describe('AUTH-32, ADR 0033 : refus de la fin d’inscription OAuth', () => {
  it.each([
    ['USERNAME_TAKEN', { kind: 'username', reason: 'taken' }],
    ['USERNAME_INVALID', { kind: 'username', reason: 'invalid' }],
    ['USERNAME_RESERVED', { kind: 'username', reason: 'reserved' }],
  ] as const)('AUTH-05 : %s, sous l’identifiant', (code, failure) => {
    expect(oauthRegistrationFailureOf(new ApiError(409, code))).toEqual(failure)
  })

  it('SEC-01 : VALIDATION_FAILED sur le nom affiché, sous son champ', () => {
    expect(
      oauthRegistrationFailureOf(
        new ApiError(400, 'VALIDATION_FAILED', { fields: ['displayName'] }),
      ),
    ).toEqual({ kind: 'displayName' })
    expect(
      oauthRegistrationFailureOf(new ApiError(400, 'VALIDATION_FAILED', { fields: ['username'] })),
    ).toEqual({ kind: 'username', reason: 'invalid' })
    expect(
      oauthRegistrationFailureOf(new ApiError(400, 'VALIDATION_FAILED', { fields: ['autre'] })),
    ).toEqual({ kind: 'unexpected' })
  })

  it.each([
    [new ApiError(404, 'NOT_FOUND'), 'REQUEST_REJECTED'],
    [new ApiError(409, 'OAUTH_ALREADY_LINKED'), 'REQUEST_REJECTED'],
    [new ApiError(409, 'OAUTH_EMAIL_IN_USE'), 'OAUTH_EMAIL_IN_USE'],
  ])('ADR 0033 (point 2) : %o renvoie à la connexion avec %s', (error, code) => {
    expect(oauthRegistrationFailureOf(error)).toEqual({ kind: 'abandoned', error: code })
  })

  it('SEC-10, A10 : limite, réseau et erreur inattendue restent des alertes', () => {
    expect(
      oauthRegistrationFailureOf(new ApiError(429, 'RATE_LIMITED', { retryAfterSeconds: 120 })),
    ).toEqual({
      kind: 'rate-limited',
      minutes: 2,
    })
    expect(oauthRegistrationFailureOf(new NetworkError())).toEqual({ kind: 'network' })
    expect(oauthRegistrationFailureOf(new ApiError(403, 'FORBIDDEN'))).toEqual({
      kind: 'unexpected',
    })
  })
})
