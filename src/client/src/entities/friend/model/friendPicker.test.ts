import { describe, expect, it } from 'vitest'
import type { Friend } from '../api/IFriendsApi'
import { affinity, rankFriends } from './friendPicker'

const NOW = new Date('2026-11-24T12:00:00Z')
const DAY = 86_400_000

let next = 0
function friend(username: string, displayName: string, stats: Partial<Friend> = {}): Friend {
  next += 1
  return {
    userId: `0192f7a1-0000-7000-8000-${String(next).padStart(12, '0')}`,
    username,
    displayName,
    friendsSince: new Date(0),
    lastInteractionAt: null,
    interactionCount30d: 0,
    ...stats,
  }
}

const usernames = (ranked: ReturnType<typeof rankFriends>) => ranked.map((r) => r.friend.username)

describe('entities/friend : classement du sélecteur (CDC 10.3, FRD-20)', () => {
  it('FRD-20 (étape 3) : paliers identifiant puis mot du nom, puis identifiant puis nom qui contiennent', () => {
    const friends = [
      friend('zoe.martin', 'Zoé Martin'), // palier 1 : le mot « Martin » commence par « mar »
      friend('amarante', 'Ana Rante'), // palier 2 : l'identifiant contient « mar »
      friend('mariel', 'Mariel Roy'), // palier 0
      friend('leo', 'Léo Lamarre'), // palier 3 : « mar » au milieu d'un mot du nom
      friend('nathan', 'Nathan Roux'), // exclu
    ]

    const ranked = rankFriends(friends, 'MAR', new Set(), NOW)

    expect(ranked.map((r) => [r.friend.username, r.tier])).toEqual([
      ['mariel', 0],
      ['zoe.martin', 1],
      ['amarante', 2],
      ['leo', 3],
    ])
  })

  it('FRD-20 (étape 1) : @ initial, espaces, casse et diacritiques ignorés', () => {
    const elodie = friend('elodie_b', 'Élodie Bérubé')

    expect(rankFriends([elodie], ' @ÉLO', new Set(), NOW)[0]?.tier).toBe(0)
    expect(rankFriends([elodie], 'beru', new Set(), NOW)[0]?.tier).toBe(1)
  })

  it('FRD-20 (étape 3) : une saisie vide met tout le monde au palier 0, sans surlignage', () => {
    const ranked = rankFriends([friend('b', 'B'), friend('a', 'A')], '   ', new Set(), NOW)

    expect(ranked.map((r) => r.tier)).toEqual([0, 0])
    expect(ranked.every((r) => r.usernameMatch === null && r.nameMatch === null)).toBe(true)
  })

  it('FRD-20 (étape 2) : les amis déjà choisis ou déjà membres sont exclus', () => {
    const chosen = friend('anna', 'Anna')
    const member = friend('annie', 'Annie')
    const free = friend('annick', 'Annick')

    expect(
      usernames(
        rankFriends([chosen, member, free], 'ann', new Set([chosen.userId, member.userId]), NOW),
      ),
    ).toEqual(['annick'])
  })

  it('FRD-20 (étape 4) : palier, puis affinité décroissante, puis identifiant ; 5 au plus', () => {
    const friends = [
      friend('emma', 'Emma'),
      friend('eric', 'Éric', {
        interactionCount30d: 12,
        lastInteractionAt: new Date(NOW.getTime() - DAY),
      }),
      friend('eve', 'Ève', { interactionCount30d: 3 }),
      friend('elsa', 'Elsa'),
      friend('enzo', 'Enzo'),
      friend('ezra', 'Ezra'),
    ]

    expect(usernames(rankFriends(friends, 'e', new Set(), NOW))).toEqual([
      'eric',
      'eve',
      'elsa',
      'emma',
      'enzo',
    ])
  })

  it('FRD-20 : affinité de la formule de 10.3, sans NaN quand personne n’a échangé', () => {
    const quiet = friend('a', 'A')
    const recent = friend('b', 'B', {
      interactionCount30d: 9,
      lastInteractionAt: new Date(NOW.getTime() - 7 * DAY),
    })

    expect(affinity(quiet, 0, NOW)).toBe(0)
    // 0,6 · ln(10)/ln(10) + 0,4 · 2^(−7/7) = 0,8
    expect(affinity(recent, 9, NOW)).toBeCloseTo(0.8, 10)
    // Δ en jours fractionnaires : 12 heures donnent 0,4 · 2^(−0,5/7).
    const halfDay = friend('c', 'C', { lastInteractionAt: new Date(NOW.getTime() - DAY / 2) })
    expect(affinity(halfDay, 9, NOW)).toBeCloseTo(0.4 * 2 ** (-0.5 / 7), 10)
  })

  it('FRD-20 (étape 4) : la partie qui correspond est repérée dans le texte affiché', () => {
    const [byName] = rankFriends([friend('xavier', 'Élodie Rémy')], 'elo', new Set(), NOW)
    const [inName] = rankFriends([friend('yann', 'Anne-Élodie')], 'elo', new Set(), NOW)
    const [byUsername] = rankFriends([friend('mx.elodie', 'Max')], 'elo', new Set(), NOW)

    expect(byName?.nameMatch).toEqual([0, 3])
    expect('Élodie Rémy'.slice(0, 3)).toBe('Élo')
    expect(inName?.tier).toBe(1)
    expect(inName?.nameMatch).toEqual([5, 8])
    expect(byUsername?.tier).toBe(2)
    expect(byUsername?.usernameMatch).toEqual([3, 6])
  })

  it('FRD-20, maquette Nouvelle-Conversation : la saisie est surlignée dans le nom et dans l’identifiant', () => {
    const [mathis] = rankFriends([friend('mathis_b', 'Mathis Bouchard')], 'ma', new Set(), NOW)
    const [thomas] = rankFriends([friend('thomas.lavoie', 'Thomas Lavoie')], 'ma', new Set(), NOW)

    expect(mathis?.usernameMatch).toEqual([0, 2])
    expect(mathis?.nameMatch).toEqual([0, 2])
    expect(thomas?.tier).toBe(2)
    expect(thomas?.usernameMatch).toEqual([3, 5])
    expect(thomas?.nameMatch).toEqual([3, 5])
  })

  it('FRD-20 : un caractère décomposé du nom affiché garde ses repères dans le texte d’origine', () => {
    const decomposed = 'E\u0301lodie'.normalize('NFD')
    const [ranked] = rankFriends([friend('z', `Zoe ${decomposed}`)], 'elo', new Set(), NOW)

    expect(ranked?.nameMatch).toEqual([4, 4 + 'E\u0301lo'.length])
  })
})
