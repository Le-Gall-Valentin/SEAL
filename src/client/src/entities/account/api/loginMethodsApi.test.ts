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

const { loginMethodsApi } = await import('./loginMethodsApi')

describe('entities/account : adaptateur des méthodes de connexion (ADR 0035)', () => {
  afterEach(() => {
    calls.length = 0
    vi.resetAllMocks()
  })

  it('AUTH-55, ADR 0035 : mot de passe et fournisseurs liés, dans l’ordre rendu par le serveur', async () => {
    request.mockResolvedValue({
      passwordSet: true,
      providers: [{ provider: 'google' }, { provider: 'github' }],
    })

    await expect(loginMethodsApi.loginMethods()).resolves.toEqual({
      passwordSet: true,
      providers: ['google', 'github'],
    })
    expect(calls).toEqual(['request /api/v1/account/identities'])
  })

  it('AUTH-55, ADR 0035 : compte OAuth sans mot de passe', async () => {
    request.mockResolvedValue({ passwordSet: false, providers: [{ provider: 'github' }] })

    await expect(loginMethodsApi.loginMethods()).resolves.toEqual({
      passwordSet: false,
      providers: ['github'],
    })
  })

  it.each([
    ['un fournisseur inconnu', { passwordSet: true, providers: [{ provider: 'okta' }] }],
    [
      'un fournisseur répété',
      { passwordSet: false, providers: [{ provider: 'google' }, { provider: 'google' }] },
    ],
    ['un champ de plus', { passwordSet: true, providers: [], totp: true }],
    [
      'un champ de plus dans un fournisseur',
      { passwordSet: true, providers: [{ provider: 'google', email: 'a@b.c' }] },
    ],
    ['passwordSet absent', { providers: [] }],
    ['passwordSet hors du contrat', { passwordSet: 'oui', providers: [] }],
  ])('SEC-01 : %s refuse la réponse', async (_cas, response) => {
    request.mockResolvedValue(response)

    await expect(loginMethodsApi.loginMethods()).rejects.toThrow()
  })
})
