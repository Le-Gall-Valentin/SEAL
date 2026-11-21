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

const { oauthProvidersApi } = await import('./oauthProvidersApi')

describe('entities/oauth-provider : adaptateur des fournisseurs configurés', () => {
  afterEach(() => {
    calls.length = 0
    vi.resetAllMocks()
  })

  it('AUTH-35 : les fournisseurs configurés, dans l’ordre rendu par le serveur', async () => {
    request.mockResolvedValue({ providers: ['google', 'github'] })

    await expect(oauthProvidersApi.providers()).resolves.toEqual(['google', 'github'])
    expect(calls).toEqual(['request /api/v1/auth/providers'])
  })

  it('OPS-03 : aucun fournisseur configuré, une liste vide', async () => {
    request.mockResolvedValue({ providers: [] })

    await expect(oauthProvidersApi.providers()).resolves.toEqual([])
  })

  it.each([
    ['inconnu', { providers: ['google', 'okta'] }],
    ['répété', { providers: ['google', 'google'] }],
    ['microsoft, absent jusqu’à AUTH-34', { providers: ['microsoft'] }],
    ['hors du contrat', { providers: 'google' }],
  ])('SEC-01 : un fournisseur %s refuse la réponse', async (_cas, response) => {
    request.mockResolvedValue(response)

    await expect(oauthProvidersApi.providers()).rejects.toThrow()
  })
})
