import { describe, expect, it } from 'vitest'
import {
  ApiError,
  isUnauthenticated,
  NetworkError,
  retryAfterMinutes,
  toErrorCode,
} from './apiErrors'

describe('Erreurs typées de l’API (CDC, section 14.6)', () => {
  it('QA-05 : un code connu est gardé, un code inconnu devient INTERNAL_ERROR', () => {
    expect(toErrorCode('INVALID_CREDENTIALS')).toBe('INVALID_CREDENTIALS')
    expect(toErrorCode('CODE_EXPIRED')).toBe('CODE_EXPIRED')
    expect(toErrorCode('NOUVEAU_CODE')).toBe('INTERNAL_ERROR')
    expect(toErrorCode('toString')).toBe('INTERNAL_ERROR')
  })

  it('ADR 0015 : le délai de Retry-After est arrondi à la minute supérieure, une minute au moins', () => {
    expect(retryAfterMinutes(61)).toBe(2)
    expect(retryAfterMinutes(60)).toBe(1)
    expect(retryAfterMinutes(900)).toBe(15)
    expect(retryAfterMinutes(1)).toBe(1)
    expect(retryAfterMinutes(0)).toBe(1)
  })

  it('AUTH-53 : seul UNAUTHENTICATED signale une session perdue, pas INVALID_CREDENTIALS', () => {
    expect(isUnauthenticated(new ApiError(401, 'UNAUTHENTICATED'))).toBe(true)
    expect(isUnauthenticated(new ApiError(401, 'INVALID_CREDENTIALS'))).toBe(false)
    expect(isUnauthenticated(new NetworkError())).toBe(false)
    expect(isUnauthenticated(new Error('UNAUTHENTICATED'))).toBe(false)
  })

  it('SEC-21 : une erreur ne porte que son code, jamais le texte du serveur', () => {
    const error = new ApiError(429, 'RATE_LIMITED', { retryAfterSeconds: 61, fields: [] })

    expect(error.message).toBe('RATE_LIMITED')
    expect(error.status).toBe(429)
    expect(error.retryAfterSeconds).toBe(61)
    expect(error.fields).toEqual([])
  })
})
