import { describe, expect, it } from 'vitest'
import { ApiError, NetworkError } from '@/shared/lib'
import { canSubmit, loginAlertOf } from './loginAlert'

describe('features/login : règles du formulaire de connexion', () => {
  it('AUTH-21 : INVALID_CREDENTIALS, seul refus attendu, a son message', () => {
    expect(loginAlertOf(new ApiError(401, 'INVALID_CREDENTIALS'))).toEqual({
      kind: 'invalid-credentials',
    })
  })

  it('AUTH-22 : 429 donne le délai en minutes', () => {
    expect(loginAlertOf(new ApiError(429, 'RATE_LIMITED', { retryAfterSeconds: 900 }))).toEqual({
      kind: 'rate-limited',
      minutes: 15,
    })
  })

  it('ADR 0024, point 8 : réseau, et tout autre refus comme erreur inattendue', () => {
    expect(loginAlertOf(new NetworkError())).toEqual({ kind: 'network' })
    expect(loginAlertOf(new ApiError(403, 'FORBIDDEN'))).toEqual({ kind: 'unexpected' })
    expect(loginAlertOf(new ApiError(400, 'VALIDATION_FAILED'))).toEqual({ kind: 'unexpected' })
  })

  it('SEC-01 : envoi permis avec un identifiant et un mot de passe dans les bornes du serveur', () => {
    expect(canSubmit({ login: 'valentin', password: 'x' })).toBe(true)
    expect(canSubmit({ login: '', password: 'x' })).toBe(false)
    expect(canSubmit({ login: '   ', password: 'x' })).toBe(false)
    expect(canSubmit({ login: 'valentin', password: '' })).toBe(false)
    expect(canSubmit({ login: 'a'.repeat(255), password: 'x' })).toBe(false)
    expect(canSubmit({ login: 'valentin', password: 'x'.repeat(513) })).toBe(false)
  })
})
