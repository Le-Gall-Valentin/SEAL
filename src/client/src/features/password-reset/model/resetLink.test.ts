import { describe, expect, it } from 'vitest'
import { ApiError, NetworkError } from '@/shared/lib'
import { linkState, minutesLeft, readResetToken, resetFailureOf } from './resetLink'

const TOKEN = 'Ab0-_'.repeat(8) + 'xyz'

describe('ADR 0022 : jeton lu dans le fragment du lien', () => {
  it('ADR 0022, point 2 : « #jeton= » suivi de 43 caractères base64url', () => {
    expect(TOKEN).toHaveLength(43)
    expect(readResetToken(`#jeton=${TOKEN}`)).toBe(TOKEN)
  })

  it.each([
    '',
    '#',
    '#jeton=',
    `#jeton=${TOKEN}x`,
    `#jeton=${TOKEN.slice(1)}`,
    `#token=${TOKEN}`,
    `#jeton=${TOKEN.slice(0, 42)}=`,
  ])('ADR 0022 : « %s » ne donne aucun jeton', (hash) => {
    expect(readResetToken(hash)).toBeNull()
  })
})

describe('Nouveau-Mot-de-passe : « Ce lien expire dans N minutes »', () => {
  it('ADR 0022 : minutes restantes, arrondies au-dessus, une au moins', () => {
    const now = Date.parse('2026-11-13T20:00:00Z')
    expect(minutesLeft(new Date('2026-11-13T20:24:00Z'), now)).toBe(24)
    expect(minutesLeft(new Date('2026-11-13T20:23:01Z'), now)).toBe(24)
    expect(minutesLeft(new Date('2026-11-13T20:00:20Z'), now)).toBe(1)
    expect(minutesLeft(new Date('2026-11-13T19:59:00Z'), now)).toBe(1)
  })
})

describe('AUTH-70, ADR 0024 : refus de la réinitialisation', () => {
  it.each([
    [new ApiError(422, 'CODE_EXPIRED'), { kind: 'expired-link' }],
    [new ApiError(400, 'VALIDATION_FAILED', { fields: ['token'] }), { kind: 'expired-link' }],
    [new ApiError(422, 'PASSWORD_TOO_WEAK'), { kind: 'password' }],
    [new ApiError(400, 'VALIDATION_FAILED', { fields: ['email'] }), { kind: 'email' }],
    [
      new ApiError(429, 'RATE_LIMITED', { retryAfterSeconds: 3600 }),
      { kind: 'rate-limited', minutes: 60 },
    ],
    [new ApiError(403, 'FORBIDDEN'), { kind: 'unexpected' }],
    [new NetworkError(), { kind: 'network' }],
  ])('AUTH-70, ADR 0024 : %s', (error, expected) => {
    expect(resetFailureOf(error)).toEqual(expected)
  })
})

describe('AUTH-70, ADR 0024 : état du lien de Nouveau-Mot-de-passe', () => {
  it.each([
    ['sans jeton', { hasToken: false, linkDead: false, failure: null, loaded: false }, 'dead'],
    [
      'lien mort à la confirmation',
      { hasToken: true, linkDead: true, failure: null, loaded: true },
      'dead',
    ],
    [
      'lien expiré à la lecture',
      { hasToken: true, linkDead: false, failure: { kind: 'expired-link' }, loaded: false },
      'dead',
    ],
    [
      'panne de la lecture',
      { hasToken: true, linkDead: false, failure: { kind: 'network' }, loaded: false },
      'error',
    ],
    [
      'lecture en cours',
      { hasToken: true, linkDead: false, failure: null, loaded: false },
      'loading',
    ],
    ['lien lu', { hasToken: true, linkDead: false, failure: null, loaded: true }, 'ready'],
  ] as const)('ADR 0024 : %s', (_cas, input, expected) => {
    expect(linkState(input)).toBe(expected)
  })
})
