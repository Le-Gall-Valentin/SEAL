import { afterEach, describe, expect, it, vi } from 'vitest'
import type { z } from 'zod'

const request = vi.fn<(path: string, schema: z.ZodType, options?: unknown) => Promise<unknown>>()

vi.mock('@/shared/api', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/shared/api')>()),
  request: async (path: string, schema: z.ZodType, options?: unknown) =>
    schema.parse(await request(path, schema, options)),
}))

const { renameGroupApi } = await import('./renameGroupApi')

const ID = '0193a1b2-0000-7000-8000-000000000003'

describe('features/rename-group : adaptateur du renommage (GRP-03)', () => {
  afterEach(() => {
    vi.resetAllMocks()
  })

  it('GRP-03, SEC-01 : PATCH du nom en NFC, vue rendue', async () => {
    request.mockResolvedValue({
      id: ID,
      type: 'GROUP',
      name: 'Été',
      members: [
        {
          userId: '0192f7a1-0000-7000-8000-000000000001',
          username: 'moi',
          displayName: 'Moi',
          role: 'ADMIN',
        },
      ],
      createdAt: '2026-11-24T10:00:00Z',
      lastServerSeq: 0,
      unreadCount: 0,
      lastSenderSeq: null,
      lastEnvelope: null,
    })

    const group = await renameGroupApi.rename(
      ID,
      `E${String.fromCodePoint(0x301)}te${String.fromCodePoint(0x301)}`,
    )

    expect(request).toHaveBeenCalledWith(`/api/v1/conversations/${ID}`, expect.anything(), {
      method: 'PATCH',
      body: { name: 'Été' },
    })
    expect(group.name).toBe('Été')
  })
})
