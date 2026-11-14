import { describe, expect, it } from 'vitest'
import { isDisplayNameValid } from './displayName'
import { isEmailWellFormed, localPartOf } from './email'
import { isReservedUsername, isUsernameWellFormed, usernameSuggestionFrom } from './username'

describe('AUTH-01, AUTH-02 : forme de l’identifiant et noms réservés', () => {
  it.each(['val', 'valentin.dupont2', 'a_b.c', 'abcdefghijklmnopqrst', '123'])(
    'AUTH-01 : « %s » est bien formé',
    (username) => {
      expect(isUsernameWellFormed(username)).toBe(true)
    },
  )

  it.each([
    'va',
    'abcdefghijklmnopqrstu',
    'Valentin',
    '.val',
    'val.',
    'va..l',
    'va._l',
    'val entin',
    'valéntin',
    '',
  ])('AUTH-01 : « %s » est mal formé', (username) => {
    expect(isUsernameWellFormed(username)).toBe(false)
  })

  it('AUTH-02 : liste complète des noms réservés (ADR 0007, point 5)', () => {
    for (const name of [
      'admin',
      'root',
      'system',
      'seal',
      'support',
      'security',
      'api',
      'null',
      'me',
      'uqac',
      'administrator',
      'moderator',
      'postmaster',
      'webmaster',
      'hostmaster',
      'abuse',
      'noreply',
      'help',
      'info',
      'contact',
      'anonymous',
      'deleted',
      'test',
    ]) {
      expect(isReservedUsername(name), name).toBe(true)
    }
    expect(isReservedUsername('valentin')).toBe(false)
  })
})

describe('AUTH-04 : identifiant suggéré à partir du courriel', () => {
  it.each([
    ['Valéntin.Le-Gall+uqac@exemple.ca', 'valentin.le.gall'],
    ['valentin.dupont@uqac.ca', 'valentin.dupont'],
    ['Jean__Paul@exemple.ca', 'jean.paul'],
    ['...a.b.c...@exemple.ca', 'a.b.c'],
    ['ab@exemple.ca', 'user'],
    ['été@exemple.ca', 'ete'],
    ['abcdefghijklmnopqrs.tuv@exemple.ca', 'abcdefghijklmnopqrs'],
    ['张伟@exemple.ca', 'user'],
  ])('AUTH-04 : %s donne %s', (email, suggestion) => {
    expect(usernameSuggestionFrom(email)).toBe(suggestion)
  })

  it('AUTH-04 : une suggestion est toujours un identifiant bien formé', () => {
    for (const email of ['x.y.z.w.v.u.t.s.r.q.p.o@a.ca', '--__--@a.ca', 'ÆØÅ.ŒŸ@a.ca']) {
      expect(isUsernameWellFormed(usernameSuggestionFrom(email)), email).toBe(true)
    }
  })
})

describe('ADR 0019, point 5 : forme du courriel', () => {
  it.each([
    'valentin@exemple.ca',
    'valentin.dupont+seal@uqac.ca',
    "o'neil@exemple.co.uk",
    'élodie@exemple.ca',
    'élodie@exemple.ca',
  ])('« %s » est bien formé, après NFC', (email) => {
    expect(isEmailWellFormed(email)).toBe(true)
  })

  it.each([
    '',
    'valentin',
    'valentin@',
    '@exemple.ca',
    'va@lentin@exemple.ca',
    '.valentin@exemple.ca',
    'valentin.@exemple.ca',
    'va..lentin@exemple.ca',
    'valentin@exemple',
    'valentin@-exemple.ca',
    'valentin@exemple-.ca',
    'valentin@exem_ple.ca',
    'valentin@exemple..ca',
    'val entin@exemple.ca',
    'valentin@exémple.ca',
    `${'a'.repeat(65)}@exemple.ca`,
    `a@${'b'.repeat(64)}.ca`,
    `${'a'.repeat(64)}@${'b'.repeat(63)}.${'c'.repeat(63)}.${'d'.repeat(60)}.ca`,
    'val‎entin@exemple.ca',
  ])('« %s » est mal formé', (email) => {
    expect(isEmailWellFormed(email)).toBe(false)
  })

  it('AUTH-11 : partie locale, avant le premier « + » ou « @ »', () => {
    expect(localPartOf('annabelle+seal@exemple.ca')).toBe('annabelle')
    expect(localPartOf('valentin@exemple.ca')).toBe('valentin')
    expect(localPartOf('a@b+c.ca')).toBe('a')
    expect(localPartOf('sans-arobase')).toBe('')
  })
})

describe('AUTH-10 : nom affiché', () => {
  it.each(['Valentin Dupont', 'V', 'é'.repeat(50), '\u{1F9AD}'.repeat(50), '👩‍💻 Valentin'])(
    '« %s » est accepté',
    (name) => {
      expect(isDisplayNameValid(name)).toBe(true)
    },
  )

  it.each([
    ['vide', ''],
    ['51 caractères', 'é'.repeat(51)],
    ['espaces seuls', '   '],
    ['remplissage hangeul seul', 'ㅤ'],
    ['contrôle', 'Val\u0007entin'],
    ['direction', 'Val‮entin'],
    ['séparateur de ligne', 'Val entin'],
    ['demi-paire isolée', 'Val\uD800entin'],
  ])('« %s » est refusé', (_cas, name) => {
    expect(isDisplayNameValid(name)).toBe(false)
  })
})
