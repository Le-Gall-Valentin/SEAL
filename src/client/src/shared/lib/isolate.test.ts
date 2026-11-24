import { describe, expect, it } from 'vitest'
import { isolate } from './isolate'

describe('shared/lib : isolation d’un texte libre (ADR 0017, ADR 0038 point 9)', () => {
  it('FRD-11, ADR 0017 : le texte est encadré par FSI et PDI, sans autre changement', () => {
    expect(isolate('Inès Côté')).toBe('\u2068Inès Côté\u2069')
    expect(isolate('')).toBe('\u2068\u2069')
  })
})
