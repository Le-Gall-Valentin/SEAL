import { describe, expect, it } from 'vitest'
import {
  proposeUsername,
  registerView,
  statusAfterRefusal,
  usernameFromEmail,
} from './registerForm'

const MET = { length: 'met', context: 'met', blocklist: 'met' } as const
const VALUES = {
  email: 'valentin@uqac.ca',
  displayName: 'Valentin',
  password: 'fjord-tuque-raquette',
  confirm: 'fjord-tuque-raquette',
}
const UNTOUCHED = { email: false, displayName: false, confirm: false }

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

describe('AUTH-10, SEC-01 : formulaire d’inscription', () => {
  const view = (overrides: Partial<Parameters<typeof registerView>[0]> = {}) =>
    registerView({
      values: VALUES,
      username: 'valentin',
      status: { kind: 'available' },
      rules: MET,
      touched: UNTOUCHED,
      refused: {},
      ...overrides,
    })

  it('SEC-01 : formulaire complet et valide, envoi permis', () => {
    expect(view()).toEqual({
      emailInvalid: false,
      displayNameInvalid: false,
      confirmMismatch: false,
      valid: true,
    })
  })

  it('ADR 0024, point 9 : courriel et nom affiché signalés une fois quittés, et pas vides', () => {
    expect(view({ values: { ...VALUES, email: 'valentin@' } }).emailInvalid).toBe(false)
    expect(
      view({ values: { ...VALUES, email: 'valentin@' }, touched: { ...UNTOUCHED, email: true } })
        .emailInvalid,
    ).toBe(true)
    expect(
      view({ values: { ...VALUES, email: '' }, touched: { ...UNTOUCHED, email: true } })
        .emailInvalid,
    ).toBe(false)
    expect(
      view({
        values: { ...VALUES, displayName: '   ' },
        touched: { ...UNTOUCHED, displayName: true },
      }).displayNameInvalid,
    ).toBe(true)
  })

  it('SEC-01 : une valeur refusée par le serveur reste signalée tant qu’elle n’a pas changé', () => {
    expect(view({ refused: { email: VALUES.email } })).toMatchObject({
      emailInvalid: true,
      valid: false,
    })
    expect(view({ refused: { displayName: VALUES.displayName } })).toMatchObject({
      displayNameInvalid: true,
      valid: false,
    })
  })

  it.each([
    ['courriel mal formé', { values: { ...VALUES, email: 'valentin' } }],
    ['identifiant pris', { status: { kind: 'taken', suggestions: [] } }],
    ['identifiant en cours de vérification', { status: { kind: 'checking' } }],
    ['identifiant réservé', { username: 'admin', status: { kind: 'available' } }],
    ['nom affiché vide', { values: { ...VALUES, displayName: '' } }],
    ['règle non respectée', { rules: { ...MET, context: 'unmet' } }],
    ['confirmation différente', { values: { ...VALUES, confirm: 'autre' } }],
  ] as const)('SEC-01 : %s, envoi impossible', (_cas, overrides) => {
    expect(view(overrides).valid).toBe(false)
  })

  it('AUTH-05 : disponibilité inconnue (réseau, 429), le serveur tranchera', () => {
    expect(view({ status: { kind: 'unknown' } }).valid).toBe(true)
  })
})
