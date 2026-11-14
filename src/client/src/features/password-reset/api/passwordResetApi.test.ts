import { afterEach, describe, expect, it, vi } from 'vitest'
import type { z } from 'zod'

const requestNoContent = vi.fn<(path: string, options?: unknown) => Promise<void>>()
const request = vi.fn<(path: string, options?: unknown) => Promise<unknown>>()

vi.mock('@/shared/api', () => ({
  requestNoContent: (path: string, options?: unknown) => requestNoContent(path, options),
  request: async (path: string, schema: z.ZodType, options?: unknown) =>
    schema.parse(await request(path, options)),
}))

const { passwordResetApi } = await import('./passwordResetApi')

const TOKEN = 'a'.repeat(43)

describe('features/password-reset : adaptateur des trois routes de l’ADR 0022', () => {
  afterEach(() => {
    vi.resetAllMocks()
  })

  it('AUTH-70 : la demande envoie le courriel', async () => {
    await passwordResetApi.request('valentin@uqac.ca')

    expect(requestNoContent).toHaveBeenCalledWith('/api/v1/auth/password-reset/request', {
      method: 'POST',
      body: { email: 'valentin@uqac.ca' },
    })
  })

  it('ADR 0022 : la lecture du lien envoie le jeton dans le corps, et lit identifiant, courriel et expiration', async () => {
    request.mockResolvedValue({
      username: 'valentin.dupont',
      email: 'valentin@uqac.ca',
      expiresAt: '2026-11-13T20:24:00Z',
    })

    const link = await passwordResetApi.inspect(TOKEN)

    expect(request).toHaveBeenCalledWith('/api/v1/auth/password-reset/inspect', {
      method: 'POST',
      body: { token: TOKEN },
    })
    expect(link).toEqual({
      username: 'valentin.dupont',
      email: 'valentin@uqac.ca',
      expiresAt: new Date('2026-11-13T20:24:00Z'),
    })
  })

  it('SEC-01 : une lecture hors du contrat est refusée', async () => {
    request.mockResolvedValue({
      username: 'valentin',
      email: 'valentin@uqac.ca',
      expiresAt: 'demain',
    })

    await expect(passwordResetApi.inspect(TOKEN)).rejects.toThrow()
  })

  it('AUTH-70 : la confirmation envoie le jeton et le nouveau mot de passe, sans ouvrir de session', async () => {
    await passwordResetApi.confirm(TOKEN, 'raquette-sous-la-neige')

    expect(requestNoContent).toHaveBeenCalledWith('/api/v1/auth/password-reset/confirm', {
      method: 'POST',
      body: { token: TOKEN, password: 'raquette-sous-la-neige' },
    })
  })
})
