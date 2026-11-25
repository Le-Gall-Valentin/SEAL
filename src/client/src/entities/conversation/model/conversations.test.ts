import { describe, expect, it } from 'vitest'
import type { Conversation, Member } from '../api/IConversationsApi'
import {
  canAdd,
  canLeave,
  canRemove,
  canRename,
  counterpartOf,
  isAdmin,
  isGroupNameValid,
  matchConversation,
  otherFirstNames,
  seatsLeft,
  sortConversations,
  titleOf,
} from './conversations'

const ME = 'me'
const member = (userId: string, displayName: string, role: Member['role'] = 'MEMBER'): Member => ({
  userId,
  username: `${userId}.u`,
  displayName,
  role,
})

function conversation(overrides: Partial<Conversation>): Conversation {
  return {
    id: 'c1',
    type: 'GROUP',
    name: 'Ski du dimanche',
    members: [member(ME, 'Moi', 'ADMIN'), member('camille', 'Camille Tremblay')],
    createdAt: new Date('2026-11-20T10:00:00Z'),
    lastServerSeq: 0,
    ...overrides,
  }
}

const direct = conversation({
  type: 'DIRECT',
  name: null,
  members: [member(ME, 'Moi'), member('mathis', 'Mathis Bouchard')],
})

describe('entities/conversation : règles de la liste et des groupes (CNV-04, CNV-10, GRP-03)', () => {
  it('CNV-04 : une directe se nomme par son interlocuteur ; sans lui, elle n’a pas de titre', () => {
    expect(counterpartOf(direct, ME)?.userId).toBe('mathis')
    expect(titleOf(direct, ME)).toBe('Mathis Bouchard')
    expect(titleOf(conversation({}), ME)).toBe('Ski du dimanche')
    const alone = conversation({ type: 'DIRECT', name: null, members: [member(ME, 'Moi')] })
    expect(counterpartOf(alone, ME)).toBeUndefined()
    expect(titleOf(alone, ME)).toBeUndefined()
  })

  it('CNV-04, ADR 0041 (point 2) : la plus récemment créée d’abord, puis par identifiant', () => {
    const older = conversation({ id: 'a', createdAt: new Date('2026-11-01T00:00:00Z') })
    const newer = conversation({ id: 'b', createdAt: new Date('2026-11-23T00:00:00Z') })
    const tie = conversation({ id: 'c', createdAt: new Date('2026-11-23T00:00:00Z') })

    expect(sortConversations([older, newer, tie]).map((c) => c.id)).toEqual(['c', 'b', 'a'])
  })

  it('CNV-10 : la recherche porte sur le nom du groupe ou sur l’interlocuteur d’une directe, sans accents', () => {
    expect(matchConversation(conversation({}), ME, 'dimanche')).toBe(true)
    expect(matchConversation(direct, ME, 'bouch')).toBe(true)
    expect(matchConversation(direct, ME, '@mathis')).toBe(true)
    expect(matchConversation(direct, ME, 'moi')).toBe(false)
    expect(matchConversation(conversation({ name: 'Équipe' }), ME, 'equipe')).toBe(true)
    expect(matchConversation(conversation({}), ME, '  ')).toBe(true)
  })

  it('GRP-03 : seul l’administrateur renomme et retire, jamais lui-même ; tout membre ajoute et part', () => {
    const group = conversation({})
    const asMember = conversation({
      members: [member(ME, 'Moi'), member('camille', 'Camille Tremblay', 'ADMIN')],
    })

    expect(isAdmin(group, ME)).toBe(true)
    expect(canRename(group, ME)).toBe(true)
    expect(canRemove(group, ME, 'camille')).toBe(true)
    expect(canRemove(group, ME, ME)).toBe(false)
    expect(canRename(asMember, ME)).toBe(false)
    expect(canRemove(asMember, ME, 'camille')).toBe(false)
    expect(canAdd(asMember)).toBe(true)
    expect(canLeave(asMember)).toBe(true)
  })

  it('ADR 0039 (point 3) : une directe ne se gère pas', () => {
    expect(canRename(direct, ME)).toBe(false)
    expect(canRemove(direct, ME, 'mathis')).toBe(false)
    expect(canAdd(direct)).toBe(false)
    expect(canLeave(direct)).toBe(false)
  })

  it('GRP-01 : 50 membres au plus ; un groupe plein n’accepte plus d’ajout', () => {
    const full = conversation({
      members: Array.from({ length: 50 }, (_, i) => member(String(i), 'X')),
    })

    expect(seatsLeft(conversation({}))).toBe(48)
    expect(seatsLeft(full)).toBe(0)
    expect(canAdd(full)).toBe(false)
  })
})

describe('entities/conversation : nom d’un groupe (GRP-01, ADR 0040)', () => {
  it('GRP-01, ADR 0040 : 1 à 64 caractères après NFC, sans caractère invisible', () => {
    expect(isGroupNameValid('Ski du dimanche')).toBe(true)
    expect(isGroupNameValid('é'.repeat(64))).toBe(true)
    expect(isGroupNameValid('e\u0301'.repeat(64))).toBe(true)
    expect(isGroupNameValid('é'.repeat(65))).toBe(false)
    expect(isGroupNameValid('   ')).toBe(false)
    expect(isGroupNameValid(`S${String.fromCodePoint(0x200b)}ki`)).toBe(false)
  })
})

describe('entities/conversation : sous-titre d’un groupe (CNV-04, maquette Conv-Ordinateur)', () => {
  it('CNV-04 : les prénoms des autres membres, dans l’ordre d’arrivée, sans l’appelant', () => {
    const group = conversation({
      members: [
        member('camille', 'Camille Tremblay'),
        member(ME, 'Moi'),
        member('ines', 'Inès  Côté'),
      ],
    })

    expect(otherFirstNames(group, ME)).toEqual(['Camille', 'Inès'])
  })

  it('CNV-04, ACC-03 : un premier mot qui ne s’affiche pas (remplissage, marques seules) laisse le nom entier', () => {
    const group = conversation({
      members: [
        member(ME, 'Moi'),
        member('filler', '\u3164 Camille'),
        member('marks', '\u0301\u0302 Mathis'),
        member('single', 'Inès'),
      ],
    })

    expect(otherFirstNames(group, ME)).toEqual(['\u3164 Camille', '\u0301\u0302 Mathis', 'Inès'])
  })
})
