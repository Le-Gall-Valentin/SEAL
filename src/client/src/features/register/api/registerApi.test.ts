import { afterEach, describe, expect, it, vi } from 'vitest'
import type { z } from 'zod'
import { NetworkError } from '@/shared/lib'

const calls: string[] = []
const requestNoContent = vi.fn<(path: string, options?: unknown) => Promise<void>>()
const reloadCsrf = vi.fn<() => Promise<void>>()
const request = vi.fn<(path: string, schema: z.ZodType, options?: unknown) => Promise<unknown>>()

vi.mock('@/shared/api', () => ({
  requestNoContent: (path: string, options?: unknown) => {
    calls.push(`requestNoContent ${path}`)
    return requestNoContent(path, options)
  },
  reloadCsrf: () => {
    calls.push('reloadCsrf')
    return reloadCsrf()
  },
  request: async (path: string, schema: z.ZodType, options?: unknown) => {
    calls.push(`request ${path}`)
    return schema.parse(await request(path, schema, options))
  },
}))

const { registerApi } = await import('./registerApi')

const REGISTRATION = {
  email: 'valentin@exemple.ca',
  username: 'valentin',
  displayName: 'Valentin',
  password: 'fjord-tuque-raquette',
}

describe('features/register : adaptateur de l’inscription et de la disponibilité', () => {
  afterEach(() => {
    calls.length = 0
    vi.resetAllMocks()
  })

  it('AUTH-10, AUTH-60 : envoie les quatre champs, puis recharge le jeton CSRF', async () => {
    requestNoContent.mockResolvedValue(undefined)
    reloadCsrf.mockResolvedValue(undefined)

    await registerApi.register(REGISTRATION)

    expect(calls).toEqual(['requestNoContent /api/v1/auth/register', 'reloadCsrf'])
    expect(requestNoContent).toHaveBeenCalledWith('/api/v1/auth/register', {
      method: 'POST',
      body: REGISTRATION,
    })
  })

  it('AUTH-05 : l’identifiant est encodé dans l’adresse, la réponse validée', async () => {
    request.mockResolvedValue({ available: false, suggestions: ['val2', 'val_x', 'val3'] })

    const availability = await registerApi.usernameAvailability('a&b=c')

    expect(calls).toEqual(['request /api/v1/auth/username-availability?u=a%26b%3Dc'])
    expect(availability).toEqual({ available: false, suggestions: ['val2', 'val_x', 'val3'] })
  })

  it('SEC-01 : une réponse hors du contrat est refusée', async () => {
    request.mockResolvedValue({ available: 'oui', suggestions: [] })

    await expect(registerApi.usernameAvailability('valentin')).rejects.toThrow()
  })

  it('AUTH-60, A10 : une inscription réussie reste réussie si le rechargement du jeton échoue', async () => {
    requestNoContent.mockResolvedValue(undefined)
    reloadCsrf.mockRejectedValue(new NetworkError())

    await expect(registerApi.register(REGISTRATION)).resolves.toBeUndefined()
  })
})
