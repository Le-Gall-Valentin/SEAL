import { z } from 'zod'
import { reloadCsrf, request, requestNoContent } from '@/shared/api'
import type { IRegisterApi } from './IRegisterApi'

/** SEC-01 : réponse de username-availability (ADR 0019) ; trois suggestions au plus, identifiants d'AUTH-01. */
const AVAILABILITY = z.object({
  available: z.boolean(),
  suggestions: z.array(z.string().min(3).max(20)).max(3),
})

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
    // AUTH-60 : l'inscription ouvre une session et retire le cookie XSRF-TOKEN (ADR 0019).
    await reloadCsrf()
  },
  usernameAvailability(username, signal) {
    return request(
      `/api/v1/auth/username-availability?u=${encodeURIComponent(username)}`,
      AVAILABILITY,
      signal ? { signal } : {},
    )
  },
}
