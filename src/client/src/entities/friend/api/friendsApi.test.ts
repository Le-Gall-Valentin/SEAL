import { afterEach, describe, expect, it, vi } from 'vitest'
import type { z } from 'zod'

const calls: string[] = []
const request = vi.fn<(path: string, schema: z.ZodType, options?: unknown) => Promise<unknown>>()

vi.mock('@/shared/api', () => ({
  request: async (path: string, schema: z.ZodType, options?: unknown) => {
    calls.push(path)
    return schema.parse(await request(path, schema, options))
  },
}))

const { friendsApi } = await import('./friendsApi')

const USER = '0192f7a1-0000-7000-8000-000000000001'
const REQUEST = '0193a1b2-0000-7000-8000-000000000002'

describe('entities/friend : adaptateur des listes d’amis et d’invitations (14.4, ADR 0037)', () => {
  afterEach(() => {
    calls.length = 0
    vi.resetAllMocks()
  })

  it('FRD-10, ADR 0037 (point 3) : une page d’amis, dates converties, curseur suivant', async () => {
    request.mockResolvedValue({
      items: [
        {
          userId: USER,
          username: 'chloe.f',
          displayName: 'Chloé Fortin',
          friendsSince: '2026-11-20T10:00:00.123Z',
          lastInteractionAt: null,
          interactionCount30d: 0,
        },
      ],
      next: USER,
    })

    const page = await friendsApi.friends(null)

    expect(calls).toEqual(['/api/v1/friends?limit=50'])
    expect(page.next).toBe(USER)
    expect(page.items[0]).toEqual({
      userId: USER,
      username: 'chloe.f',
      displayName: 'Chloé Fortin',
      friendsSince: new Date('2026-11-20T10:00:00.123Z'),
    })
  })

  it('ADR 0037 (point 3) : le curseur reçu part en after, encodé', async () => {
    request.mockResolvedValue({ items: [], next: null })

    await friendsApi.friends(USER)
    await friendsApi.receivedRequests(REQUEST)
    await friendsApi.sentRequests(null)

    expect(calls).toEqual([
      `/api/v1/friends?limit=50&after=${USER}`,
      `/api/v1/friend-requests?direction=incoming&limit=50&after=${REQUEST}`,
      '/api/v1/friend-requests?direction=outgoing&limit=50',
    ])
  })

  it('API-01 : invitations reçues avec le nom affiché, envoyées sans', async () => {
    request.mockResolvedValueOnce({
      items: [
        {
          id: REQUEST,
          userId: USER,
          username: 'sarah.b',
          displayName: 'Sarah',
          createdAt: '2026-11-22T10:00:00Z',
        },
      ],
      next: null,
    })
    request.mockResolvedValueOnce({
      items: [
        { id: REQUEST, userId: USER, username: 'antoine.roy', createdAt: '2026-11-22T10:00:00Z' },
      ],
      next: null,
    })

    const received = await friendsApi.receivedRequests(null)
    const sent = await friendsApi.sentRequests(null)

    expect(received.items[0]?.displayName).toBe('Sarah')
    expect(sent.items[0]).toEqual({
      id: REQUEST,
      userId: USER,
      username: 'antoine.roy',
      createdAt: new Date('2026-11-22T10:00:00Z'),
    })
  })

  it.each([
    ['un champ inconnu', { items: [], next: null, total: 3 }],
    ['un curseur qui n’est pas un UUID', { items: [], next: 'pas-un-uuid' }],
    ['plus de 50 éléments', { items: Array.from({ length: 51 }, () => ({})), next: null }],
    [
      'un nom affiché dans une invitation envoyée',
      {
        items: [
          {
            id: REQUEST,
            userId: USER,
            username: 'antoine.roy',
            displayName: 'Antoine',
            createdAt: '2026-11-22T10:00:00Z',
          },
        ],
        next: null,
      },
    ],
  ])('SEC-01, API-01 : %s refuse la réponse', async (_cas, body) => {
    request.mockResolvedValue(body)

    await expect(friendsApi.sentRequests(null)).rejects.toThrow()
  })
})
