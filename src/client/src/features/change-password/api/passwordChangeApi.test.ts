import { afterEach, describe, expect, it, vi } from 'vitest'

const calls: string[] = []

vi.mock('@/shared/api', () => ({
  requestNoContent: (path: string, options: { method: string; body: unknown }) => {
    calls.push(`${options.method} ${path} ${JSON.stringify(options.body)}`)
    return Promise.resolve()
  },
  reloadCsrf: () => {
    calls.push('reloadCsrf')
    return Promise.reject(new Error('réseau'))
  },
}))

const { passwordChangeApi } = await import('./passwordChangeApi')

describe('features/change-password : adaptateur', () => {
  afterEach(() => {
    calls.length = 0
  })

  it('ACC-01, AUTH-60 : les deux mots de passe et l’option partent, puis le jeton CSRF est rechargé', async () => {
    await expect(
      passwordChangeApi.changePassword({
        currentPassword: 'ancienne phrase',
        newPassword: 'raquette-sous-la-neige',
        signOutOtherSessions: false,
      }),
    ).resolves.toBeUndefined()

    expect(calls).toEqual([
      'POST /api/v1/account/password {"currentPassword":"ancienne phrase","newPassword":"raquette-sous-la-neige","signOutOtherSessions":false}',
      'reloadCsrf',
    ])
  })
})
