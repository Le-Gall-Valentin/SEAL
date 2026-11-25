import { afterEach, describe, expect, it, vi } from 'vitest'

const requestNoContent = vi.fn<(path: string, options?: unknown) => Promise<void>>()

vi.mock('@/shared/api', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/shared/api')>()),
  requestNoContent: (path: string, options?: unknown) => requestNoContent(path, options),
}))

const { leaveGroupApi } = await import('./leaveGroupApi')

const ID = '0193a1b2-0000-7000-8000-000000000003'

describe('features/leave-group : adaptateur (GRP-03)', () => {
  afterEach(() => {
    vi.resetAllMocks()
  })

  it('GRP-03 : POST sur l’adresse du groupe, 204 attendu', async () => {
    requestNoContent.mockResolvedValue(undefined)

    await leaveGroupApi.leave(ID)

    expect(requestNoContent).toHaveBeenCalledWith(`/api/v1/conversations/${ID}/leave`, {
      method: 'POST',
    })
  })
})
