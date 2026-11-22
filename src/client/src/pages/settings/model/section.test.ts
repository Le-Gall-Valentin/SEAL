import { describe, expect, it } from 'vitest'
import { sectionOf } from './section'

describe('ADR 0035 : sous-page des paramètres sur téléphone', () => {
  it('ADR 0035 : « compte » ouvre Compte ; toute autre valeur, ou aucune, la liste des sections', () => {
    expect(sectionOf(new URLSearchParams('section=compte'))).toBe('compte')
    expect(sectionOf(new URLSearchParams('section=securite'))).toBeNull()
    expect(sectionOf(new URLSearchParams('section=Compte'))).toBeNull()
    expect(sectionOf(new URLSearchParams(''))).toBeNull()
  })
})
