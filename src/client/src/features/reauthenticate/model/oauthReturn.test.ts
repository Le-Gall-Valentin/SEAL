import { describe, expect, it } from 'vitest'
import { reauthReturnErrorOf } from './oauthReturn'

describe('AUTH-55, ADR 0034 : retour refusé d’une ré-authentification OAuth', () => {
  it.each([
    ['?erreur=REQUEST_REJECTED', 'REQUEST_REJECTED'],
    ['?erreur=RATE_LIMITED', 'RATE_LIMITED'],
    ['?erreur=OAUTH_EMAIL_IN_USE', null],
    ['?erreur=request_rejected', null],
    ['?erreur=REQUEST_REJECTED&erreur=RATE_LIMITED', null],
    ['?section=compte', null],
    ['', null],
  ])('ADR 0034 : %s → %s', (search, expected) => {
    expect(reauthReturnErrorOf(search)).toBe(expected)
  })
})
