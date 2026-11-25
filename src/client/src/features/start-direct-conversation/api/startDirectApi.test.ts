import { afterEach, describe, expect, it, vi } from 'vitest'
import type { z } from 'zod'

const request = vi.fn<(path: string, schema: z.ZodType, options?: unknown) => Promise<unknown>>()

vi.mock('@/shared/api', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/shared/api')>()),
  request: async (path: string, schema: z.ZodType, options?: unknown) =>
    schema.parse(await request(path, schema, options)),
}))

const { startDirectApi } = await import('./startDirectApi')

const ME = '0192f7a1-0000-7000-8000-000000000001'
const FRIEND = '0192f7a1-0000-7000-8000-000000000002'
const DIRECT = {
  id: '0193a1b2-0000-7000-8000-000000000003',
  type: 'DIRECT',
  name: null,
  members: [
    { userId: ME, username: 'moi', displayName: 'Moi', role: 'MEMBER' },
    { userId: FRIEND, username: 'camille.t', displayName: 'Camille', role: 'MEMBER' },
  ],
  createdAt: '2026-11-24T20:10:25Z',
  lastServerSeq: 0,
  unreadCount: 0,
  lastSenderSeq: null,
  lastEnvelope: null,
}

describe('features/start-direct-conversation : adaptateur de direct (CNV-01)', () => {
  afterEach(() => {
    vi.resetAllMocks()
  })

  it('CNV-01 : envoie l’ami et rend la directe, trouvée ou créée', async () => {
    request.mockResolvedValue({ created: false, conversation: DIRECT })

    const opened = await startDirectApi.openDirect(FRIEND)

    expect(request).toHaveBeenCalledWith('/api/v1/conversations/direct', expect.anything(), {
      method: 'POST',
      body: { userId: FRIEND },
    })
    expect(opened.created).toBe(false)
    expect(opened.conversation.type).toBe('DIRECT')
  })

  it('SEC-01 : un champ inconnu dans la réponse la fait refuser', async () => {
    request.mockResolvedValue({ created: true, conversation: DIRECT, extra: 1 })

    await expect(startDirectApi.openDirect(FRIEND)).rejects.toThrow()
  })
})
