import { describe, expect, it } from 'vitest'
import {
  proposeUsername,
  statusAfterRefusal,
  usernameFromEmail,
  usernameRefusalReason,
} from './usernameProposal'

describe('AUTH-04 : identifiant proposé à partir du courriel', () => {
  it('AUTH-04 : aucune proposition tant que le courriel n’a pas de partie locale', () => {
    expect(usernameFromEmail('')).toBe('')
    expect(usernameFromEmail('valentin')).toBe('')
    expect(usernameFromEmail('Valentin.Dupont+seal@uqac.ca')).toBe('valentin.dupont')
  })

  it.each([
    ['pris', { kind: 'taken', suggestions: ['valentin2', 'valentin_x'] }],
    ['réservé', { kind: 'reserved', suggestions: ['valentin2'] }],
  ] as const)(
    'AUTH-04, étape 5 : base %s, la première suggestion libre, disponible',
    (_cas, status) => {
      expect(proposeUsername('valentin', status)).toEqual({
        username: 'valentin2',
        status: { kind: 'available' },
      })
    },
  )

  it.each([
    { kind: 'taken', suggestions: [] },
    { kind: 'available' },
    { kind: 'checking' },
    { kind: 'unknown' },
  ] as const)('AUTH-04 : sans suggestion libre, la base reste avec son état (%o)', (status) => {
    expect(proposeUsername('valentin', status)).toEqual({ username: 'valentin', status })
  })
})

describe('AUTH-05 : refus de l’identifiant au retour du serveur', () => {
  it('AUTH-05 : un refus prime tant que la disponibilité ne l’a pas confirmé', () => {
    expect(
      statusAfterRefusal({ kind: 'available' }, { value: 'valentin', reason: 'taken' }, 'valentin'),
    ).toEqual({ kind: 'taken', suggestions: [] })
    expect(
      statusAfterRefusal(
        { kind: 'available' },
        { value: 'valentin', reason: 'invalid' },
        'valentin',
      ),
    ).toEqual({ kind: 'invalid' })
  })

  it('AUTH-05 : un autre identifiant, ou une disponibilité qui confirme, garde son état', () => {
    expect(
      statusAfterRefusal({ kind: 'available' }, { value: 'valentin', reason: 'taken' }, 'noah'),
    ).toEqual({ kind: 'available' })
    const confirmed = { kind: 'taken', suggestions: ['valentin2'] } as const
    expect(statusAfterRefusal(confirmed, { value: 'valentin', reason: 'taken' }, 'valentin')).toBe(
      confirmed,
    )
  })
})

describe('AUTH-05 : motif du refus de l’identifiant', () => {
  it.each([
    ['USERNAME_TAKEN', 'taken'],
    ['USERNAME_INVALID', 'invalid'],
    ['USERNAME_RESERVED', 'reserved'],
    ['PASSWORD_TOO_WEAK', null],
  ] as const)('AUTH-05, CDC 14.6 : %s donne %s', (code, reason) => {
    expect(usernameRefusalReason(code)).toBe(reason)
  })
})
