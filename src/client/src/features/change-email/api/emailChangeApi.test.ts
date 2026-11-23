import { afterEach, describe, expect, it, vi } from 'vitest'
import type { z } from 'zod'

const calls: string[] = []
const respond = vi.fn<() => unknown>()

vi.mock('@/shared/api', () => ({
  request: async (path: string, schema: z.ZodType, options: { method: string; body: unknown }) => {
    calls.push(`${options.method} ${path} ${JSON.stringify(options.body)}`)
    return schema.parse(await Promise.resolve(respond()))
  },
  requestNoContent: (path: string, options: { method: string; body: unknown }) => {
    calls.push(`${options.method} ${path} ${JSON.stringify(options.body)}`)
    return Promise.resolve()
  },
  reloadCsrf: () => Promise.reject(new Error('non attendu')),
}))

const { emailChangeApi } = await import('./emailChangeApi')

describe('features/change-email : adaptateur', () => {
  afterEach(() => {
    calls.length = 0
    respond.mockReset()
  })

  it('ACC-02 : la demande porte la nouvelle adresse ; la confirmation porte le code et rend le compte', async () => {
    respond.mockReturnValue({
      id: '0192f0c4-7b1a-7cc0-9a51-3f6f8e2d4b10',
      username: 'valentin.dupont',
      displayName: 'Valentin Dupont',
      email: 'neuve@exemple.ca',
      status: 'ACTIVE',
      verification: null,
    })

    await emailChangeApi.request('neuve@exemple.ca')
    await expect(emailChangeApi.confirm('123456')).resolves.toMatchObject({
      email: 'neuve@exemple.ca',
    })
    expect(calls).toEqual([
      'POST /api/v1/account/email {"email":"neuve@exemple.ca"}',
      'POST /api/v1/account/email/confirm {"code":"123456"}',
    ])
  })

  it('SEC-01 : un compte rendu hors du contrat refuse la confirmation', async () => {
    respond.mockReturnValue({ id: 'pas-un-uuid', email: 'neuve@exemple.ca' })

    await expect(emailChangeApi.confirm('123456')).rejects.toThrow()
  })
})
