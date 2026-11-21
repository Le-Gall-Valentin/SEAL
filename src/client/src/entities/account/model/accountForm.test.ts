import { describe, expect, it } from 'vitest'
import { isDisplayNameShownInvalid } from './displayName'
import { refusedAccountField } from './refusedField'

describe('ADR 0024 (point 9), ADR 0010 : erreurs des champs du compte', () => {
  it('ACC-03 : le nom affiché est signalé une fois quitté et pas vide, ou tant qu’il garde la valeur refusée', () => {
    expect(isDisplayNameShownInvalid({ value: '', touched: true, refused: undefined })).toBe(false)
    expect(isDisplayNameShownInvalid({ value: '\u200b', touched: false, refused: undefined })).toBe(
      false,
    )
    expect(isDisplayNameShownInvalid({ value: '\u200b', touched: true, refused: undefined })).toBe(
      true,
    )
    expect(
      isDisplayNameShownInvalid({ value: 'Valentin', touched: true, refused: 'Valentin' }),
    ).toBe(true)
    expect(isDisplayNameShownInvalid({ value: 'Valentin', touched: true, refused: 'Val' })).toBe(
      false,
    )
  })

  it.each([
    [['email', 'username'], 'email'],
    [['username', 'displayName'], 'displayName'],
    [['username'], 'username'],
    [['password'], null],
    [[], null],
  ] as const)('SEC-01 : VALIDATION_FAILED sur %o, champ %s', (fields, field) => {
    expect(refusedAccountField(fields)).toBe(field)
  })
})
