import { afterEach, describe, expect, it, vi } from 'vitest'

const calls: string[] = []
const fail = vi.fn<() => boolean>(() => false)

vi.mock('@/shared/api', () => ({
  requestNoContent: (path: string, options: { method: string; body: unknown }) => {
    calls.push(`${options.method} ${path} ${JSON.stringify(options.body)}`)
    return Promise.resolve()
  },
  reloadCsrf: () => {
    calls.push('reloadCsrf')
    return fail() ? Promise.reject(new Error('réseau')) : Promise.resolve()
  },
}))

const { reauthApi } = await import('./reauthApi')

describe('features/reauthenticate : adaptateur de la ré-authentification', () => {
  afterEach(() => {
    calls.length = 0
    fail.mockReset()
  })

  it('AUTH-55, AUTH-60 : le mot de passe part, puis le jeton CSRF est rechargé', async () => {
    await reauthApi.reauthenticate('phrase secrète')

    expect(calls).toEqual(['POST /api/v1/auth/reauth {"password":"phrase secrète"}', 'reloadCsrf'])
  })

  it('AUTH-60 : un rechargement raté du jeton ne fait pas échouer la ré-authentification réussie', async () => {
    fail.mockReturnValue(true)

    await expect(reauthApi.reauthenticate('phrase secrète')).resolves.toBeUndefined()
  })
})
