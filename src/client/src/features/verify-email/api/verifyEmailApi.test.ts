import { afterEach, describe, expect, it, vi } from 'vitest'

const calls: string[] = []
const requestNoContent = vi.fn<(path: string, options?: unknown) => Promise<void>>()

vi.mock('@/shared/api', () => ({
  requestNoContent: (path: string, options?: unknown) => {
    calls.push(`requestNoContent ${path}`)
    return requestNoContent(path, options)
  },
  reloadCsrf: () => {
    calls.push('reloadCsrf')
    return Promise.resolve()
  },
}))

const { verifyEmailApi } = await import('./verifyEmailApi')

describe('features/verify-email : adaptateur de la vérification du courriel', () => {
  afterEach(() => {
    calls.length = 0
    vi.resetAllMocks()
  })

  it('AUTH-14, AUTH-60 : envoie le code, puis recharge le jeton CSRF (la session devient complète)', async () => {
    await verifyEmailApi.verify('503271')

    expect(calls).toEqual(['requestNoContent /api/v1/auth/verify-email', 'reloadCsrf'])
    expect(requestNoContent).toHaveBeenCalledWith('/api/v1/auth/verify-email', {
      method: 'POST',
      body: { code: '503271' },
    })
  })

  it('AUTH-14 : renvoi du code, sans corps', async () => {
    await verifyEmailApi.resend()

    expect(calls).toEqual(['requestNoContent /api/v1/auth/verify-email/resend'])
    expect(requestNoContent).toHaveBeenCalledWith('/api/v1/auth/verify-email/resend', {
      method: 'POST',
    })
  })

  it('AUTH-17 : changement du courriel en attente', async () => {
    await verifyEmailApi.changeEmail('noah@uqac.ca')

    expect(requestNoContent).toHaveBeenCalledWith('/api/v1/auth/verify-email/change', {
      method: 'POST',
      body: { email: 'noah@uqac.ca' },
    })
  })
})
