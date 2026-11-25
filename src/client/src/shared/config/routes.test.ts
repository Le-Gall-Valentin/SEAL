import { describe, expect, it } from 'vitest'
import {
  conversationIdOf,
  conversationPath,
  FRIENDS_INVITATIONS,
  friendsTabOf,
  SETTINGS_ACCOUNT,
  settingsSectionOf,
} from './routes'

describe('ACC-03, ADR 0035 : sous-page des paramètres sur téléphone', () => {
  it('ACC-03 : « compte » ouvre Compte ; toute autre valeur, ou aucune, n’est pas une sous-page', () => {
    expect(settingsSectionOf(new URLSearchParams('section=compte'))).toBe('compte')
    expect(settingsSectionOf(new URLSearchParams('section=securite'))).toBeNull()
    expect(settingsSectionOf(new URLSearchParams('section=Compte'))).toBeNull()
    expect(settingsSectionOf(new URLSearchParams(''))).toBeNull()
    expect(settingsSectionOf(new URL(SETTINGS_ACCOUNT, 'https://localhost').searchParams)).toBe(
      'compte',
    )
  })
})

describe('FRD-10, ADR 0038 (point 5) : onglet de l’écran Amis', () => {
  it('ADR 0038 (point 5) : seul « invitations » ouvre les invitations ; tout le reste ouvre les amis', () => {
    expect(friendsTabOf(new URL(FRIENDS_INVITATIONS, 'https://localhost').searchParams)).toBe(
      'invitations',
    )
    expect(friendsTabOf(new URLSearchParams(''))).toBe('amis')
    expect(friendsTabOf(new URLSearchParams('onglet=Invitations'))).toBe('amis')
    expect(friendsTabOf(new URLSearchParams('onglet=autre'))).toBe('amis')
    expect(FRIENDS_INVITATIONS).toBe('/amis?onglet=invitations')
  })

  it('CNV-04, ADR 0041 (point 1) : une conversation s’ouvre à /?conversation={id} ; un identifiant mal formé est ignoré', () => {
    const id = '0193a1b2-0000-7000-8000-000000000003'

    expect(conversationPath(id)).toBe(`/?conversation=${id}`)
    expect(conversationIdOf(new URL(conversationPath(id), 'https://localhost').searchParams)).toBe(
      id,
    )
    expect(conversationIdOf(new URLSearchParams('conversation=pas-un-uuid'))).toBeNull()
    expect(conversationIdOf(new URLSearchParams(''))).toBeNull()
  })
})
