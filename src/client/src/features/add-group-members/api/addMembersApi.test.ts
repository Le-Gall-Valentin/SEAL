import { afterEach, describe, expect, it, vi } from 'vitest'
import type { z } from 'zod'

const request = vi.fn<(path: string, schema: z.ZodType, options?: unknown) => Promise<unknown>>()

vi.mock('@/shared/api', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/shared/api')>()),
  request: async (path: string, schema: z.ZodType, options?: unknown) =>
    schema.parse(await request(path, schema, options)),
}))

const { addMembersApi } = await import('./addMembersApi')

const ID = '0193a1b2-0000-7000-8000-000000000003'
const ME = '0192f7a1-0000-7000-8000-000000000001'
const NEW = '0192f7a1-0000-7000-8000-000000000002'

describe('features/add-group-members : adaptateur de l’ajout (GRP-03)', () => {
  afterEach(() => {
    vi.resetAllMocks()
  })

  it('GRP-03 : envoie les arrivants à l’adresse du groupe et rend la vue', async () => {
    request.mockResolvedValue({
      id: ID,
      type: 'GROUP',
      name: 'Ski',
      members: [
        { userId: ME, username: 'moi', displayName: 'Moi', role: 'ADMIN' },
        { userId: NEW, username: 'camille.t', displayName: 'Camille', role: 'MEMBER' },
      ],
      createdAt: '2026-11-24T10:00:00Z',
      lastServerSeq: 0,
      unreadCount: 0,
      lastSenderSeq: null,
      lastEnvelope: null,
    })

    const group = await addMembersApi.admit(ID, [NEW])

    expect(request).toHaveBeenCalledWith(`/api/v1/conversations/${ID}/members`, expect.anything(), {
      method: 'POST',
      body: { userIds: [NEW] },
    })
    expect(group.members).toHaveLength(2)
  })
})
