import { describe, expect, it } from 'vitest'
import { CODE_LENGTH, enterDigits } from './codeDigits'

const EMPTY = ['', '', '', '', '', '']

describe('AUTH-14 : saisie du code à 6 chiffres', () => {
  it('AUTH-14 : un chiffre remplit sa case, et le focus passe à la suivante', () => {
    expect(enterDigits(EMPTY, 0, '5')).toEqual({ cells: ['5', '', '', '', '', ''], focus: 1 })
  })

  it('AUTH-14 : un code collé, ou proposé par le système (one-time-code), remplit les cases suivantes', () => {
    expect(enterDigits(EMPTY, 0, '503 271')).toEqual({
      cells: ['5', '0', '3', '2', '7', '1'],
      focus: 5,
    })
    expect(enterDigits(EMPTY, 4, '9876')).toEqual({ cells: ['', '', '', '', '9', '8'], focus: 5 })
  })

  it('AUTH-14 : tout autre caractère est ignoré', () => {
    expect(enterDigits(EMPTY, 2, 'a')).toEqual({ cells: EMPTY, focus: 2 })
  })

  it('AUTH-14 : six cases', () => {
    expect(CODE_LENGTH).toBe(6)
  })
})
