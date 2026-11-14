import { describe, expect, it } from 'vitest'
import { passwordBlocklist } from '../api/passwordBlocklist'

describe('AUTH-11 : liste de blocage du dépôt, chargée à la demande', () => {
  it('ADR 0007, point 4 : 10 000 entrées, la première en tête', async () => {
    const entries = await passwordBlocklist.load()

    expect(entries).toHaveLength(10_000)
    expect(entries[0]).toBe('Mailcreated5240')
  })
})
