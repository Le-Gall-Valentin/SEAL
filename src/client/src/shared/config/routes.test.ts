import { describe, expect, it } from 'vitest'
import { SETTINGS_ACCOUNT, settingsSectionOf } from './routes'

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
