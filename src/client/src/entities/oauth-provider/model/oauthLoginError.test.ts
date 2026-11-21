import { describe, expect, it } from 'vitest'
import { loginPathWithError, oauthLoginErrorOf } from './oauthLoginError'

describe('ADR 0032 (point 2), ADR 0033 (point 1) : code d’erreur du retour OAuth', () => {
  it.each(['OAUTH_EMAIL_IN_USE', 'RATE_LIMITED', 'REQUEST_REJECTED'] as const)(
    'AUTH-33, SEC-10 : %s est lu',
    (code) => {
      expect(oauthLoginErrorOf(`?erreur=${code}`)).toBe(code)
    },
  )

  it.each([
    ['sans paramètre', ''],
    ['code inconnu', '?erreur=INTERNAL_ERROR'],
    ['casse différente', '?erreur=rate_limited'],
    ['valeur vide', '?erreur='],
    ['deux valeurs', '?erreur=RATE_LIMITED&erreur=REQUEST_REJECTED'],
    ['autre paramètre', '?error=REQUEST_REJECTED'],
  ])('AUTH-35, ADR 0033 : %s, aucune alerte', (_cas, search) => {
    expect(oauthLoginErrorOf(search)).toBeNull()
  })
})

describe('ADR 0033 (point 2) : adresse de Connexion avec un code', () => {
  it.each(['OAUTH_EMAIL_IN_USE', 'RATE_LIMITED', 'REQUEST_REJECTED'] as const)(
    'AUTH-32 : %s, une adresse que la connexion relit',
    (code) => {
      const path = loginPathWithError(code)

      expect(path).toBe(`/connexion?erreur=${code}`)
      expect(oauthLoginErrorOf(path.slice(path.indexOf('?')))).toBe(code)
    },
  )
})
