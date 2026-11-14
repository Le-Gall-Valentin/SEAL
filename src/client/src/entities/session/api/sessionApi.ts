import { z } from 'zod'
import { reloadCsrf, request } from '@/shared/api'
import { ApiError } from '@/shared/lib'
import type { Account, ISessionApi } from './ISessionApi'

/** SEC-01 : réponse de /me bornée et typée ; les bornes du texte suivent le CDC, en unités UTF-16. */
const ACCOUNT = z.object({
  id: z.uuid(),
  username: z.string().min(3).max(20),
  displayName: z.string().min(1).max(200),
  email: z.string().min(3).max(254),
  status: z.enum(['PENDING_EMAIL', 'ACTIVE']),
  verification: z
    .object({
      codeExpiresAt: z.iso.datetime().nullable(),
      resendsRemaining: z.number().int().min(0).max(100),
    })
    .nullable(),
})

function toAccount(dto: z.infer<typeof ACCOUNT>): Account {
  return {
    id: dto.id,
    username: dto.username,
    displayName: dto.displayName,
    email: dto.email,
    status: dto.status,
    verification:
      dto.verification === null
        ? null
        : {
            codeExpiresAt:
              dto.verification.codeExpiresAt === null
                ? null
                : new Date(dto.verification.codeExpiresAt),
            resendsRemaining: dto.verification.resendsRemaining,
          },
  }
}

/** Adaptateur HTTP du port ISessionApi. */
export const sessionApi: ISessionApi = {
  async me(signal) {
    try {
      return toAccount(await request('/api/v1/auth/me', ACCOUNT, signal ? { signal } : {}))
    } catch (error) {
      // AUTH-16 : sans session, /me répond 401 ; c'est l'état « anonyme », pas une panne.
      if (error instanceof ApiError && error.status === 401) {
        return null
      }
      throw error
    }
  },
  reloadCsrf,
}
