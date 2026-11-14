import { describe, expect, it } from 'vitest'
import { confirmMismatch, rulesAfterRefusal } from './passwordForm'

const MET = { length: 'met', context: 'met', blocklist: 'met' } as const

describe('AUTH-11 : règles communes aux formulaires de mot de passe', () => {
  it('AUTH-11 : un mot de passe refusé par le serveur fait tomber la liste de blocage, seule règle qui échappe au client', () => {
    expect(rulesAfterRefusal(MET, 'refusé', 'refusé')).toEqual({ ...MET, blocklist: 'unmet' })
    expect(rulesAfterRefusal({ ...MET, blocklist: 'unknown' }, 'refusé', 'refusé').blocklist).toBe(
      'unmet',
    )
  })

  it('AUTH-11 : un autre mot de passe que celui refusé garde ses règles', () => {
    expect(rulesAfterRefusal(MET, 'refusé', 'autre')).toEqual(MET)
    expect(rulesAfterRefusal(MET, null, 'autre')).toEqual(MET)
  })

  it('ADR 0024, point 9 : confirmation différente signalée une fois quittée, ou dès qu’elle atteint la longueur du mot de passe', () => {
    expect(confirmMismatch({ password: 'abcdef', confirm: 'abc', confirmTouched: false })).toBe(
      false,
    )
    expect(confirmMismatch({ password: 'abcdef', confirm: 'abc', confirmTouched: true })).toBe(true)
    expect(confirmMismatch({ password: 'abcdef', confirm: 'abcdeg', confirmTouched: false })).toBe(
      true,
    )
    expect(confirmMismatch({ password: 'abcdef', confirm: 'abcdef', confirmTouched: true })).toBe(
      false,
    )
    expect(confirmMismatch({ password: 'abcdef', confirm: '', confirmTouched: true })).toBe(false)
  })
})
