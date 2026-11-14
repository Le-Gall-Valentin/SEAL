import { z } from 'zod'
import { request, requestNoContent } from '@/shared/api'
import type { IPasswordResetApi } from './IPasswordResetApi'

/** SEC-01 : réponse d'inspect (ADR 0022, point 1). */
const RESET_LINK = z.object({
  username: z.string().min(3).max(20),
  email: z.string().min(3).max(254),
  expiresAt: z.iso.datetime(),
})

/** Adaptateur HTTP du port IPasswordResetApi ; le jeton ne voyage que dans le corps des requêtes (ADR 0022). */
export const passwordResetApi: IPasswordResetApi = {
  request(email) {
    return requestNoContent('/api/v1/auth/password-reset/request', {
      method: 'POST',
      body: { email },
    })
  },
  async inspect(token) {
    const link = await request('/api/v1/auth/password-reset/inspect', RESET_LINK, {
      method: 'POST',
      body: { token },
    })
    return { username: link.username, email: link.email, expiresAt: new Date(link.expiresAt) }
  },
  confirm(token, password) {
    return requestNoContent('/api/v1/auth/password-reset/confirm', {
      method: 'POST',
      body: { token, password },
    })
  },
}
