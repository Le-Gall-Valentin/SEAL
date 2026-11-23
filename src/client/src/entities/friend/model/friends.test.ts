import { describe, expect, it } from 'vitest'
import {
  matchFriend,
  newestFirst,
  normalizeSearch,
  relativeAge,
  sortFriends,
  tintOf,
  usernameInitials,
} from './friends'

const friend = (displayName: string, username: string) => ({
  userId: '0192f7a1-0000-7000-8000-000000000001',
  username,
  displayName,
  friendsSince: new Date('2026-11-01T00:00:00Z'),
})

describe('entities/friend : règles de l’écran Amis (ADR 0038)', () => {
  it('ADR 0038 (point 6) : amis triés par nom affiché en français, puis par identifiant', () => {
    const sorted = sortFriends([
      friend('Élodie Roy', 'elodie.r'),
      friend('Chloé Fortin', 'chloe.fortin'),
      friend('camille', 'camille.b'),
      friend('Camille', 'camille.a'),
    ])

    expect(sorted.map((f) => f.username)).toEqual([
      'camille.a',
      'camille.b',
      'chloe.fortin',
      'elodie.r',
    ])
  })

  it('ADR 0038 (point 6) : invitations de la plus récente à la plus ancienne', () => {
    const older = { createdAt: new Date('2026-11-20T10:00:00Z') }
    const newer = { createdAt: new Date('2026-11-22T10:00:00Z') }

    expect(newestFirst([older, newer])).toEqual([newer, older])
  })

  it('FRD-20, 10.3 (étape 1) : saisie normalisée sans diacritiques, espaces ni @ initial', () => {
    expect(normalizeSearch('  @Chloé  Fortin ')).toBe('chloefortin')
    expect(normalizeSearch('Ｃｈｌｏé')).toBe('chloe')
  })

  it('ADR 0038 (point 6) : un ami reste s’il a la saisie dans son identifiant ou son nom affiché', () => {
    const chloe = friend('Chloé Fortin', 'chloe.f')

    expect(matchFriend(chloe, '')).toBe(true)
    expect(matchFriend(chloe, 'chloe')).toBe(true)
    expect(matchFriend(chloe, '@chloe.f')).toBe(true)
    expect(matchFriend(chloe, 'fort')).toBe(true)
    expect(matchFriend(chloe, 'tremblay')).toBe(false)
  })

  it('ADR 0038 (point 7) : initiales d’un identifiant, premier et dernier segment', () => {
    expect(usernameInitials('jean_pierre.dupont')).toBe('JD')
    expect(usernameInitials('antoine.roy')).toBe('AR')
    expect(usernameInitials('noa')).toBe('N')
    expect(usernameInitials('7even.x')).toBe('7X')
  })

  it('ADR 0038 (point 7) : teinte stable tirée de l’identifiant du compte', () => {
    const id = '0192f7a1-0000-7000-8000-00000000abcd'

    expect(tintOf(id)).toBe(tintOf(id))
    const tints = new Set(
      Array.from({ length: 24 }, (_, i) =>
        tintOf(`0192f7a1-0000-7000-8000-${String(i).padStart(12, '0')}`),
      ),
    )
    expect(tints.size).toBe(6)
  })

  it.each([
    ['2026-11-23T11:59:30Z', { unit: 'now' }],
    ['2026-11-23T11:59:00Z', { unit: 'minutes', count: 1 }],
    ['2026-11-23T11:15:00Z', { unit: 'minutes', count: 45 }],
    ['2026-11-23T10:00:00Z', { unit: 'hours', count: 2 }],
    ['2026-11-22T13:00:00Z', { unit: 'hours', count: 23 }],
    ['2026-11-22T09:00:00Z', { unit: 'yesterday' }],
    ['2026-11-20T12:00:00Z', { unit: 'days', count: 3 }],
    ['2026-11-17T12:00:00Z', { unit: 'days', count: 6 }],
    ['2026-11-12T12:00:00Z', { unit: 'date' }],
  ] as const)('ADR 0038 (point 2) : %s, vu le 23 nov. à midi', (at, expected) => {
    const now = new Date('2026-11-23T12:00:00Z')

    expect(relativeAge(new Date(at), now)).toMatchObject(expected)
  })
})
