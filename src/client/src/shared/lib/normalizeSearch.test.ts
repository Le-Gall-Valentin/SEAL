import { describe, expect, it } from 'vitest'
import { normalizeSearch } from './normalizeSearch'

describe('shared/lib : saisie de recherche normalisée (CDC 10.3, étape 1)', () => {
  it('FRD-20 : NFKD, diacritiques retirés, minuscules, espaces et @ initial supprimés', () => {
    expect(normalizeSearch('  @Chloé  Fortin ')).toBe('chloefortin')
    expect(normalizeSearch('Ｃｈｌｏé')).toBe('chloe')
    expect(normalizeSearch('')).toBe('')
  })
})
