import type { Page } from './page'

/** Garde-fou : 200 pages de 50, bien au-delà d'une liste réelle (amis, conversations). */
const MAX_PAGES = 200

/**
 * ADR 0037 (point 3) : suit `next` jusqu'à `null`, même après une page vide (comptes inactifs retirés). Un curseur
 * déjà vu ou trop de pages arrêtent la lecture en erreur plutôt que de boucler (A10).
 */
export async function allPages<T>(load: (after: string | null) => Promise<Page<T>>): Promise<T[]> {
  const items: T[] = []
  const seen = new Set<string>()
  let after: string | null = null
  for (let page = 0; page < MAX_PAGES; page++) {
    const { items: pageItems, next }: Page<T> = await load(after)
    items.push(...pageItems)
    if (next === null) {
      return items
    }
    if (seen.has(next)) {
      break
    }
    seen.add(next)
    after = next
  }
  throw new Error('pagination sans fin')
}
