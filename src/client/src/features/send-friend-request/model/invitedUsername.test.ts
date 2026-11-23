import { describe, expect, it } from 'vitest'
import { normalizeInvitedUsername } from './invitedUsername'

describe('features/send-friend-request : saisie de l’identifiant (ADR 0037, points 6 et 10)', () => {
  it.each([
    ['sarah.belanger', 'sarah.belanger'],
    ['  @Sarah.Belanger ', 'sarah.belanger'],
    ['@LEA_2', 'lea_2'],
  ])('ADR 0037 : « %s » devient « %s », comme au serveur', (input, expected) => {
    expect(normalizeInvitedUsername(input)).toBe(expected)
  })

  it.each([
    '',
    '@',
    '@@sarah',
    'ab',
    'deux mots',
    'sarah..b',
    'élodie',
    '\u001Fsarah',
    '\tsarah',
    ' sarah',
    'a'.repeat(21),
  ])('AUTH-01, ADR 0037 (point 10) : « %s » est refusé sans requête', (input) => {
    expect(normalizeInvitedUsername(input)).toBeNull()
  })
})
