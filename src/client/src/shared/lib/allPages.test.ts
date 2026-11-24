import { describe, expect, it } from 'vitest'
import { allPages } from './allPages'
import type { Page } from './page'

describe('shared/lib : toutes les pages d’une liste (ADR 0037, point 3)', () => {
  it('ADR 0037 (point 3) : suit next jusqu’à null, même après une page vide', async () => {
    const pages: Record<string, Page<number>> = {
      '': { items: [1, 2], next: 'a' },
      a: { items: [], next: 'b' },
      b: { items: [3], next: null },
    }
    const asked: (string | null)[] = []

    const items = await allPages(async (after) => {
      asked.push(after)
      return Promise.resolve(pages[after ?? ''] ?? { items: [], next: null })
    })

    expect(items).toEqual([1, 2, 3])
    expect(asked).toEqual([null, 'a', 'b'])
  })

  it('A10 : un curseur déjà vu arrête la lecture en erreur', async () => {
    await expect(allPages(async () => Promise.resolve({ items: [1], next: 'a' }))).rejects.toThrow(
      'pagination sans fin',
    )
  })

  it('A10 : au-delà de 200 pages, la lecture s’arrête en erreur', async () => {
    let page = 0
    await expect(
      allPages(async () => Promise.resolve({ items: [], next: String(page++) })),
    ).rejects.toThrow('pagination sans fin')
    expect(page).toBe(200)
  })
})
