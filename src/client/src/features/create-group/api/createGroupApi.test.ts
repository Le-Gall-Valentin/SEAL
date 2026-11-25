import { afterEach, describe, expect, it, vi } from 'vitest'
import type { z } from 'zod'

const request = vi.fn<(path: string, schema: z.ZodType, options?: unknown) => Promise<unknown>>()

vi.mock('@/shared/api', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/shared/api')>()),
  request: async (path: string, schema: z.ZodType, options?: unknown) =>
    schema.parse(await request(path, schema, options)),
}))

const { createGroupApi } = await import('./createGroupApi')

const ME = '0192f7a1-0000-7000-8000-000000000001'
const FRIEND = '0192f7a1-0000-7000-8000-000000000002'
const VIEW = {
  id: '0193a1b2-0000-7000-8000-000000000003',
  type: 'GROUP',
  name: 'Été',
  members: [
    { userId: ME, username: 'moi', displayName: 'Moi', role: 'ADMIN' },
    { userId: FRIEND, username: 'camille.t', displayName: 'Camille', role: 'MEMBER' },
  ],
  createdAt: '2026-11-24T20:10:25.537696Z',
  lastServerSeq: 0,
  unreadCount: 0,
  lastSenderSeq: null,
  lastEnvelope: null,
}

describe('features/create-group : adaptateur de la création (CNV-03, GRP-02)', () => {
  afterEach(() => {
    vi.resetAllMocks()
  })

  it('CNV-03, SEC-01 : nom envoyé en NFC avec les membres, vue rendue', async () => {
    request.mockResolvedValue(VIEW)

    const group = await createGroupApi.createGroup('E\u0301te\u0301', [FRIEND])

    expect(request).toHaveBeenCalledWith('/api/v1/conversations/group', expect.anything(), {
      method: 'POST',
      body: { name: 'Été', memberIds: [FRIEND] },
    })
    expect(group.name).toBe('Été')
    expect(group.members).toHaveLength(2)
  })
})
