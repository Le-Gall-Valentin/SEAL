import { describe, expect, it } from 'vitest'
import { initialsOf } from './initials'

describe("ACC-03, ADR 0035 : initiales de l'avatar", () => {
  it('ACC-03 : les deux premiers mots du nom affiché, en majuscules', () => {
    expect(initialsOf('Valentin Dupont')).toBe('VD')
    expect(initialsOf('léa tremblay-roy')).toBe('LT')
  })

  it('ACC-03 : un seul mot donne une lettre ; au-delà de deux mots, seuls les deux premiers comptent', () => {
    expect(initialsOf('Valentin')).toBe('V')
    expect(initialsOf('Jean Paul Sartre')).toBe('JP')
  })

  it('ACC-03 : espaces en trop ignorés ; lettre accentuée décomposée gardée entière', () => {
    expect(initialsOf('  Émile   Zola ')).toBe('ÉZ')
    expect(initialsOf('élodie Roy')).toBe('ÉR')
  })
})
