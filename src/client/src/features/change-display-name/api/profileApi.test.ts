import { afterEach, describe, expect, it, vi } from 'vitest'
import type { z } from 'zod'

const calls: string[] = []
const respond = vi.fn<() => unknown>()

vi.mock('@/shared/api', () => ({
  request: async (path: string, schema: z.ZodType, options: { method: string; body: unknown }) => {
    calls.push(`${options.method} ${path} ${JSON.stringify(options.body)}`)
    return schema.parse(await Promise.resolve(respond()))
  },
  // entities/session, qui porte le contrat du compte, importe aussi ces fonctions.
  requestNoContent: () => Promise.reject(new Error('non attendu')),
  reloadCsrf: () => Promise.reject(new Error('non attendu')),
}))

const { profileApi } = await import('./profileApi')

const ACCOUNT = {
  id: '0192f0c4-7b1a-7cc0-9a51-3f6f8e2d4b10',
  username: 'valentin.dupont',
  displayName: 'Léa Tremblay',
  email: 'valentin@uqac.ca',
  status: 'ACTIVE',
  verification: null,
}

describe('features/change-display-name : adaptateur du profil', () => {
  afterEach(() => {
    calls.length = 0
    respond.mockReset()
  })

  it('ACC-03 : le nom part en PATCH ; le compte rendu par le serveur revient', async () => {
    respond.mockReturnValue(ACCOUNT)

    await expect(profileApi.changeDisplayName('Léa Tremblay')).resolves.toMatchObject({
      displayName: 'Léa Tremblay',
      username: 'valentin.dupont',
    })
    expect(calls).toEqual(['PATCH /api/v1/account/profile {"displayName":"Léa Tremblay"}'])
  })

  it('SEC-01 : une réponse hors du contrat du compte est refusée', async () => {
    respond.mockReturnValue({ ...ACCOUNT, status: 'DELETED' })

    await expect(profileApi.changeDisplayName('Léa')).rejects.toThrow()
  })
})
