import { describe, expect, it } from 'vitest'
import type { Account } from '../api/ISessionApi'
import { destinationFor, SESSION_STATES, sessionStateOf } from './sessionState'

const ACCOUNT: Account = {
  id: '0192f0c4-7b1a-7cc0-9a51-3f6f8e2d4b10',
  username: 'valentin',
  displayName: 'Valentin',
  email: 'valentin@exemple.ca',
  status: 'ACTIVE',
  verification: null,
}

describe('entities/session : orientation selon le compte courant', () => {
  it('AUTH-16 : anonyme, en attente ou actif selon /me', () => {
    expect(sessionStateOf(null)).toBe('anonymous')
    expect(sessionStateOf({ ...ACCOUNT, status: 'PENDING_EMAIL' })).toBe('pending')
    expect(sessionStateOf(ACCOUNT)).toBe('active')
  })

  it('AUTH-16 : chaque état mène à son écran', () => {
    expect(destinationFor('anonymous')).toBe('/connexion')
    expect(destinationFor('pending')).toBe('/verification-courriel')
    expect(destinationFor('active')).toBe('/')
  })

  it('AUTH-16 : trois états, trois écrans distincts, donc aucune boucle de redirection', () => {
    const destinations = SESSION_STATES.map(destinationFor)

    expect(new Set(destinations).size).toBe(SESSION_STATES.length)
  })
})
