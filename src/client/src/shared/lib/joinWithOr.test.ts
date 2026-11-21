import { describe, expect, it } from 'vitest'
import { joinWithOr } from './joinWithOr'

describe('ADR 0033, point 3 : liste des fournisseurs', () => {
  it.each([
    [[], ''],
    [['Google'], 'Google'],
    [['Google', 'GitHub'], 'Google ou GitHub'],
    [['Microsoft', 'Google', 'GitHub'], 'Microsoft, Google ou GitHub'],
  ])('AUTH-70, ADR 0033 : %o donne « %s »', (names, expected) => {
    expect(joinWithOr(names, 'ou')).toBe(expected)
  })
})
