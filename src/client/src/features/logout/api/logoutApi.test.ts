import { afterEach, describe, expect, it, vi } from 'vitest'

const calls: string[] = []

let csrfFails = false

vi.mock('@/shared/api', () => ({
  requestNoContent: (path: string, options?: { method?: string }) => {
    calls.push(`${options?.method ?? 'GET'} ${path}`)
    return Promise.resolve()
  },
  reloadCsrf: () => {
    calls.push('reloadCsrf')
    return csrfFails ? Promise.reject(new Error('réseau')) : Promise.resolve()
  },
}))

const { logoutApi } = await import('./logoutApi')

describe('features/logout : adaptateur de POST /api/v1/auth/logout', () => {
  afterEach(() => {
    calls.length = 0
    csrfFails = false
  })

  it('AUTH-53, AUTH-60 : ferme la session, puis recharge le jeton CSRF', async () => {
    await logoutApi.logout()

    expect(calls).toEqual(['POST /api/v1/auth/logout', 'reloadCsrf'])
  })

  it('AUTH-60, A10 : une déconnexion réussie reste réussie si le rechargement du jeton échoue', async () => {
    csrfFails = true

    await expect(logoutApi.logout()).resolves.toBeUndefined()
  })
})
