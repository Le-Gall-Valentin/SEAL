import { afterEach, describe, expect, it, vi } from 'vitest'
import { NetworkError } from '@/shared/lib'

const calls: string[] = []
const requestNoContent = vi.fn<(path: string, options?: unknown) => Promise<void>>()
const reloadCsrf = vi.fn<() => Promise<void>>()

vi.mock('@/shared/api', () => ({
  requestNoContent: (path: string, options?: unknown) => {
    calls.push(`requestNoContent ${path}`)
    return requestNoContent(path, options)
  },
  reloadCsrf: () => {
    calls.push('reloadCsrf')
    return reloadCsrf()
  },
}))

const { registerApi } = await import('./registerApi')

const REGISTRATION = {
  email: 'valentin@exemple.ca',
  username: 'valentin',
  displayName: 'Valentin',
  password: 'fjord-tuque-raquette',
}

describe('features/register : adaptateur de l’inscription', () => {
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

  it('AUTH-60, A10 : une inscription réussie reste réussie si le rechargement du jeton échoue', async () => {
    requestNoContent.mockResolvedValue(undefined)
    reloadCsrf.mockRejectedValue(new NetworkError())

    await expect(registerApi.register(REGISTRATION)).resolves.toBeUndefined()
  })
})
