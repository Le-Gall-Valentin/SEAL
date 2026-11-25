import { describe, expect, it } from 'vitest'
import { z } from 'zod'
import { PAGE_LIMIT, pagePath, pageSchema } from './page'

const NEXT = '0192f7a1-ffff-7000-8000-00000000a11c'

describe('shared/api : pages par curseur (ADR 0037, point 3)', () => {
  it('CNV-04, FRD-10, ADR 0037 (point 3) : limit de 50, after encodé, séparateur selon la base', () => {
    expect(PAGE_LIMIT).toBe(50)
    expect(pagePath('/api/v1/friends', null)).toBe('/api/v1/friends?limit=50')
    expect(pagePath('/api/v1/friends', 'a b')).toBe('/api/v1/friends?limit=50&after=a%20b')
    expect(pagePath('/api/v1/friend-requests?direction=incoming', null)).toBe(
      '/api/v1/friend-requests?direction=incoming&limit=50',
    )
  })

  it('SEC-01 : une page a 50 éléments au plus, un curseur UUID ou null, et aucun champ inconnu', () => {
    const schema = pageSchema(z.int())

    expect(schema.parse({ items: [1], next: NEXT })).toEqual({ items: [1], next: NEXT })
    expect(
      schema.safeParse({ items: Array.from({ length: 51 }, () => 1), next: null }).success,
    ).toBe(false)
    expect(schema.safeParse({ items: [], next: 'pas-un-uuid' }).success).toBe(false)
    expect(schema.safeParse({ items: [], next: null, total: 3 }).success).toBe(false)
  })
})
