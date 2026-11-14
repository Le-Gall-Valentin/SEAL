import { reloadCsrf, requestNoContent } from '@/shared/api'
import type { IVerifyEmailApi } from './IVerifyEmailApi'

/** Adaptateur HTTP du port IVerifyEmailApi. */
export const verifyEmailApi: IVerifyEmailApi = {
  async verify(code) {
    // La réponse 200 porte le compte courant ; il est relu par /me, seule source de la session.
    await requestNoContent('/api/v1/auth/verify-email', { method: 'POST', body: { code } })
    // AUTH-60 : la vérification change la session et retire le cookie XSRF-TOKEN (ADR 0019).
    await reloadCsrf()
  },
  resend() {
    return requestNoContent('/api/v1/auth/verify-email/resend', { method: 'POST' })
  },
  changeEmail(email) {
    return requestNoContent('/api/v1/auth/verify-email/change', { method: 'POST', body: { email } })
  },
}
