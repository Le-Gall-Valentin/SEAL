import { afterEach, describe, expect, it, vi } from 'vitest'
import type { z } from 'zod'

const calls: string[] = []
const request = vi.fn<(path: string, schema: z.ZodType, options?: unknown) => Promise<unknown>>()

vi.mock('@/shared/api', () => ({
  request: async (path: string, schema: z.ZodType, options?: unknown) => {
    calls.push(`request ${path}`)
    return schema.parse(await request(path, schema, options))
  },
}))

const { usernameAvailabilityApi } = await import('./usernameAvailabilityApi')

describe('entities/account : adaptateur de la disponibilité', () => {
  afterEach(() => {
    calls.length = 0
    vi.resetAllMocks()
  })

  it('AUTH-05 : l’identifiant est encodé dans l’adresse, la réponse validée', async () => {
    request.mockResolvedValue({ available: false, suggestions: ['val2', 'val_x', 'val3'] })

    const availability = await usernameAvailabilityApi.usernameAvailability('a&b=c')

    expect(calls).toEqual(['request /api/v1/auth/username-availability?u=a%26b%3Dc'])
    expect(availability).toEqual({ available: false, suggestions: ['val2', 'val_x', 'val3'] })
  })

  it('SEC-01 : une réponse hors du contrat est refusée', async () => {
    request.mockResolvedValue({ available: 'oui', suggestions: [] })

    await expect(usernameAvailabilityApi.usernameAvailability('valentin')).rejects.toThrow()
  })
})
