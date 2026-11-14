import { describe, expect, it } from 'vitest'
import { passwordStrength } from './passwordStrength'

const MET = { length: 'met', context: 'met', blocklist: 'met' } as const

describe('ADR 0007, point 6 : jauge « Robustesse »', () => {
  it('ADR 0007 : niveau 0 tant qu’une règle n’est pas respectée', () => {
    expect(passwordStrength('x'.repeat(30), { ...MET, context: 'unmet' })).toBe(0)
    expect(passwordStrength('x'.repeat(30), { ...MET, blocklist: 'unknown' })).toBe(0)
  })

  it.each([
    [15, 1],
    [17, 1],
    [18, 2],
    [19, 2],
    [20, 3],
    [24, 3],
    [25, 4],
    [128, 4],
  ])('ADR 0007 : %i points de code donnent le niveau %i', (length, level) => {
    expect(passwordStrength('x'.repeat(length), MET)).toBe(level)
  })

  it('ADR 0007 : la longueur se compte après NFKC, en points de code', () => {
    expect(passwordStrength('\u{1F9AD}'.repeat(20), MET)).toBe(3)
    expect(passwordStrength('ﬁ'.repeat(9), MET)).toBe(2)
  })
})
