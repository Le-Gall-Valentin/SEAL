import { describe, expect, it } from 'vitest'
import { EMPTY_GROUPS, enterGroups, recoveryKeyOf } from './recoveryKeyText'

const KEY = ['8WQK', '3MZD', 'T7PA', 'R2HX', '5NCF', 'Y9GB', 'K4VE', 'J6TS']

describe('KEY-33 : saisie de la clé de récupération en 8 cases (Restauration-Appareil)', () => {
  it('KEY-33 : un groupe tapé remplit sa case ; complet, le focus passe à la suivante', () => {
    expect(enterGroups(EMPTY_GROUPS, 2, 'T7P')).toEqual({
      groups: ['', '', 'T7P', '', '', '', '', ''],
      focus: 2,
    })
    expect(enterGroups(EMPTY_GROUPS, 2, 'T7PA').focus).toBe(3)
    expect(enterGroups(KEY, 7, 'J6TS').focus).toBe(7)
  })

  it('KEY-33 : une clé collée d’un bloc se répartit dans les cases, minuscules, espaces et tirets compris', () => {
    for (const pasted of [KEY.join(''), KEY.join(' '), KEY.join('-').toLowerCase()]) {
      const entered = enterGroups(EMPTY_GROUPS, 0, pasted)
      expect(entered.groups.map((group) => group.toUpperCase())).toEqual(KEY)
      expect(entered.focus).toBe(7)
    }
  })

  it('KEY-33 : un collage au milieu remplit les cases suivantes, sans dépasser la huitième', () => {
    const entered = enterGroups(EMPTY_GROUPS, 6, KEY.join(''))

    expect(entered.groups).toEqual(['', '', '', '', '', '', '8WQK', '3MZD'])
    expect(entered.focus).toBe(7)
  })

  it('KEY-33 : une case vidée reste sur place', () => {
    expect(enterGroups(KEY, 4, '')).toEqual({
      groups: ['8WQK', '3MZD', 'T7PA', 'R2HX', '', 'Y9GB', 'K4VE', 'J6TS'],
      focus: 4,
    })
  })

  it('KEY-33 : seules 32 caractères valides font une clé ; la saisie n’est jamais mise en majuscules par le code', () => {
    expect(recoveryKeyOf(KEY)).not.toBeNull()
    expect(recoveryKeyOf(KEY.map((group) => group.toLowerCase()))).not.toBeNull()
    expect(recoveryKeyOf([...KEY.slice(0, 7), 'J6T'])).toBeNull()
    expect(recoveryKeyOf([...KEY.slice(0, 7), 'J6TU'])).toBeNull()
    expect(recoveryKeyOf([...KEY.slice(0, 7), 'J6Tı'])).toBeNull()
    expect(enterGroups(EMPTY_GROUPS, 0, 'ı').groups[0]).toBe('ı')
  })

  it('KEY-33 : un caractère tapé dans une case pleine n’écrase pas la suivante ; seul un collage se répartit', () => {
    expect(enterGroups(KEY, 2, 'T7ZPA')).toEqual({ groups: KEY, focus: 2 })
    expect(enterGroups(KEY, 7, 'J6TSX')).toEqual({ groups: KEY, focus: 7 })
    expect(enterGroups(KEY, 2, 'T7PAR2HX').groups).toEqual(KEY)
  })
})
