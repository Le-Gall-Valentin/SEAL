import { z } from 'zod'
import { isDisplayNameValid, isUsernameWellFormed } from '@/entities/account/@x/session'
import { reloadCsrf, request } from '@/shared/api'
import { ApiError } from '@/shared/lib'
import type { Account, ISessionApi } from './ISessionApi'

/**
 * SEC-01 : réponse de /me bornée et typée ; les bornes du texte suivent le CDC, en unités UTF-16, et les noms les règles
 * d'écriture d'AUTH-01 et d'ACC-03 (ADR 0038, point 9).
 */
const ACCOUNT = z.object({
  id: z.uuid(),
  username: z.string().max(20).refine(isUsernameWellFormed),
  displayName: z.string().max(200).refine(isDisplayNameValid),
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

/**
 * Le compte courant tel que le rendent /me et les routes du compte qui le renvoient (ACC-02, ACC-03, ADR 0034) : une
 * seule définition du contrat.
 */
export const ACCOUNT_RESPONSE = ACCOUNT.transform(toAccount)

/** Adaptateur HTTP du port ISessionApi. */
export const sessionApi: ISessionApi = {
  async me(signal) {
    try {
      return await request('/api/v1/auth/me', ACCOUNT_RESPONSE, signal ? { signal } : {})
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
