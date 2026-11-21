import { reloadCsrf, requestNoContent } from '@/shared/api'
import type { IRegisterApi } from './IRegisterApi'

/** Adaptateur HTTP du port IRegisterApi. */
export const registerApi: IRegisterApi = {
  async register(registration) {
    // La réponse 201 porte le compte courant ; il est relu par /me, seule source de la session.
    await requestNoContent('/api/v1/auth/register', {
      method: 'POST',
      body: {
        email: registration.email,
        username: registration.username,
        displayName: registration.displayName,
        password: registration.password,
      },
    })
    // AUTH-60 : l'inscription ouvre une session et retire le cookie XSRF-TOKEN (ADR 0019). Un échec ne défait pas
    // l'inscription réussie : la première requête non sûre rechargera le jeton absent (shared/api).
    await reloadCsrf().catch(() => undefined)
  },
}
