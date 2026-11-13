import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '@/shared/lib'

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

const { loginApi } = await import('./loginApi')

describe('features/login : adaptateur de POST /api/v1/auth/login', () => {
  afterEach(() => {
    calls.length = 0
    vi.resetAllMocks()
  })

  it('AUTH-20, AUTH-60 : envoie identifiant et mot de passe, puis recharge le jeton CSRF', async () => {
    requestNoContent.mockResolvedValue(undefined)
    reloadCsrf.mockResolvedValue(undefined)

    await loginApi.login({ login: 'valentin', password: 'fjord-tuque-raquette' })

    expect(calls).toEqual(['requestNoContent /api/v1/auth/login', 'reloadCsrf'])
    expect(requestNoContent).toHaveBeenCalledWith('/api/v1/auth/login', {
      method: 'POST',
      body: { login: 'valentin', password: 'fjord-tuque-raquette' },
    })
  })

  it('AUTH-21 : un refus remonte tel quel, sans recharger le jeton', async () => {
    requestNoContent.mockRejectedValue(new ApiError(401, 'INVALID_CREDENTIALS'))

    await expect(loginApi.login({ login: 'valentin', password: 'faux' })).rejects.toMatchObject({
      code: 'INVALID_CREDENTIALS',
    })
    expect(reloadCsrf).not.toHaveBeenCalled()
  })
})
