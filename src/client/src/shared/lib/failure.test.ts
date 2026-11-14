import { describe, expect, it } from 'vitest'
import { ApiError, NetworkError } from './apiErrors'
import { failureOf } from './failure'

describe('ADR 0024, point 8 : classement d’un échec pour l’affichage', () => {
  it('ADR 0024 : serveur injoignable', () => {
    expect(failureOf(new NetworkError())).toEqual({ kind: 'network' })
  })

  it('SEC-10, ADR 0015 : limite atteinte, délai en minutes arrondi au-dessus', () => {
    expect(failureOf(new ApiError(429, 'RATE_LIMITED', { retryAfterSeconds: 61 }))).toEqual({
      kind: 'rate-limited',
      minutes: 2,
    })
  })

  it('SEC-10 : sans Retry-After lisible, le délai vaut une minute', () => {
    expect(failureOf(new ApiError(429, 'RATE_LIMITED'))).toEqual({
      kind: 'rate-limited',
      minutes: 1,
    })
  })

  it('QA-05 : un refus prévu garde son code', () => {
    expect(failureOf(new ApiError(401, 'INVALID_CREDENTIALS'))).toEqual({
      kind: 'refused',
      code: 'INVALID_CREDENTIALS',
    })
  })

  it.each([
    ['INTERNAL_ERROR', new ApiError(500, 'INTERNAL_ERROR')],
    ['une erreur 5xx', new ApiError(503, 'REQUEST_REJECTED')],
    ['une erreur JavaScript', new TypeError('x')],
    ['une valeur quelconque', 'boom'],
  ])('ADR 0024 : %s est une erreur inattendue', (_cas, error) => {
    expect(failureOf(error)).toEqual({ kind: 'unexpected' })
  })
})
