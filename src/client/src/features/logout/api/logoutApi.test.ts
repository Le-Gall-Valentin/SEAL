import { afterEach, describe, expect, it, vi } from 'vitest'

const calls: string[] = []

vi.mock('@/shared/api', () => ({
  requestNoContent: (path: string, options?: { method?: string }) => {
    calls.push(`${options?.method ?? 'GET'} ${path}`)
    return Promise.resolve()
  },
  reloadCsrf: () => {
    calls.push('reloadCsrf')
    return Promise.resolve()
  },
}))

const { logoutApi } = await import('./logoutApi')

describe('features/logout : adaptateur de POST /api/v1/auth/logout', () => {
  afterEach(() => {
    calls.length = 0
  })

  it('AUTH-53, AUTH-60 : ferme la session, puis recharge le jeton CSRF', async () => {
    await logoutApi.logout()

    expect(calls).toEqual(['POST /api/v1/auth/logout', 'reloadCsrf'])
  })
})
