import { afterEach, describe, expect, it, vi } from 'vitest'

const requestNoContent = vi.fn<(path: string, options?: unknown) => Promise<void>>()

vi.mock('@/shared/api', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/shared/api')>()),
  requestNoContent: (path: string, options?: unknown) => requestNoContent(path, options),
}))

const { removeMemberApi } = await import('./removeMemberApi')

const ID = '0193a1b2-0000-7000-8000-000000000003'

describe('features/remove-group-member : adaptateur (GRP-03)', () => {
  afterEach(() => {
    vi.resetAllMocks()
  })

  it('GRP-03 : DELETE sur l’adresse du groupe, 204 attendu', async () => {
    requestNoContent.mockResolvedValue(undefined)

    await removeMemberApi.exclude(ID, 'u-2')

    expect(requestNoContent).toHaveBeenCalledWith(`/api/v1/conversations/${ID}/members/u-2`, {
      method: 'DELETE',
    })
  })
})
