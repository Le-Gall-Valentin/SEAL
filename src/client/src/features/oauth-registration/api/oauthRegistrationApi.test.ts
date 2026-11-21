import { afterEach, describe, expect, it, vi } from 'vitest'
import type { z } from 'zod'
import { ApiError, NetworkError } from '@/shared/lib'

const calls: string[] = []
const request = vi.fn<(path: string, schema: z.ZodType, options?: unknown) => Promise<unknown>>()
const requestNoContent = vi.fn<(path: string, options?: unknown) => Promise<void>>()
const reloadCsrf = vi.fn<() => Promise<void>>()

vi.mock('@/shared/api', () => ({
  request: async (path: string, schema: z.ZodType, options?: unknown) => {
    calls.push(`request ${path}`)
    return schema.parse(await request(path, schema, options))
  },
  requestNoContent: (path: string, options?: unknown) => {
    calls.push(`requestNoContent ${path}`)
    return requestNoContent(path, options)
  },
  reloadCsrf: () => {
    calls.push('reloadCsrf')
    return reloadCsrf()
  },
}))

const { oauthRegistrationApi } = await import('./oauthRegistrationApi')

const PENDING = {
  provider: 'google',
  email: 'valentin.dupont@gmail.com',
  displayName: 'Valentin Dupont',
  expiresAt: '2026-11-20T15:10:00Z',
}

describe('features/oauth-registration : adaptateur de l’inscription OAuth', () => {
  afterEach(() => {
    calls.length = 0
    vi.resetAllMocks()
  })

  it('AUTH-32 : l’inscription en attente, validée', async () => {
    request.mockResolvedValue(PENDING)

    await expect(oauthRegistrationApi.pending()).resolves.toEqual({
      provider: 'google',
      email: 'valentin.dupont@gmail.com',
      displayName: 'Valentin Dupont',
    })
    expect(calls).toEqual(['request /api/v1/auth/oauth/pending'])
  })

  it('AUTH-32 : un nom proposé vide est admis, l’utilisateur le saisira', async () => {
    request.mockResolvedValue({ ...PENDING, displayName: '' })

    await expect(oauthRegistrationApi.pending()).resolves.toMatchObject({ displayName: '' })
  })

  it('ADR 0033 (point 2) : aucune inscription en attente (404), null', async () => {
    request.mockRejectedValue(new ApiError(404, 'NOT_FOUND'))

    await expect(oauthRegistrationApi.pending()).resolves.toBeNull()
  })

  it('A10 : toute autre erreur remonte', async () => {
    request.mockRejectedValue(new NetworkError())

    await expect(oauthRegistrationApi.pending()).rejects.toBeInstanceOf(NetworkError)
  })

  it.each([
    ['fournisseur inconnu', { ...PENDING, provider: 'okta' }],
    ['courriel absent', { ...PENDING, email: undefined }],
    ['échéance absente', { ...PENDING, expiresAt: undefined }],
    ['courriel mal formé', { ...PENDING, email: 'pas-un-courriel' }],
    ['courriel sur deux lignes', { ...PENDING, email: 'valentin@gmail.com\nbcc@evil.example' }],
    ['nom avec un caractère de direction', { ...PENDING, displayName: 'Valentin\u202eenilav' }],
    ['nom de 51 caractères', { ...PENDING, displayName: 'n'.repeat(51) }],
  ])(
    'SEC-01, ACC-03 : %s, réponse refusée comme le serveur la refuserait',
    async (_cas, response) => {
      request.mockResolvedValue(response)

      await expect(oauthRegistrationApi.pending()).rejects.toThrow()
    },
  )

  it('AUTH-32, AUTH-60 : complete envoie les deux champs, puis recharge le jeton CSRF', async () => {
    requestNoContent.mockResolvedValue(undefined)
    reloadCsrf.mockResolvedValue(undefined)

    await oauthRegistrationApi.complete({ username: 'valentin.dupont', displayName: 'Valentin' })

    expect(calls).toEqual(['requestNoContent /api/v1/auth/oauth/complete', 'reloadCsrf'])
    expect(requestNoContent).toHaveBeenCalledWith('/api/v1/auth/oauth/complete', {
      method: 'POST',
      body: { username: 'valentin.dupont', displayName: 'Valentin' },
    })
  })

  it('AUTH-60, A10 : une inscription réussie reste réussie si le rechargement du jeton échoue', async () => {
    requestNoContent.mockResolvedValue(undefined)
    reloadCsrf.mockRejectedValue(new NetworkError())

    await expect(
      oauthRegistrationApi.complete({ username: 'valentin', displayName: 'Valentin' }),
    ).resolves.toBeUndefined()
  })
})
