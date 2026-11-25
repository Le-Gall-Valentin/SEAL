import { z } from 'zod'

/** Taille de page demandée, le plafond du serveur (ADR 0037, point 3). */
export const PAGE_LIMIT = 50

/** SEC-01 : une page de la section 14 (`{items, next}`), 50 éléments au plus, champs inconnus refusés. */
export function pageSchema<T extends z.ZodType>(item: T) {
  return z.strictObject({ items: z.array(item).max(PAGE_LIMIT), next: z.uuid().nullable() })
}

/** Adresse d'une page : `limit` de 50, puis le curseur `after` encodé s'il y en a un. */
export function pagePath(base: string, after: string | null): string {
  const separator = base.includes('?') ? '&' : '?'
  const cursor = after === null ? '' : `&after=${encodeURIComponent(after)}`
  return `${base}${separator}limit=${String(PAGE_LIMIT)}${cursor}`
}
