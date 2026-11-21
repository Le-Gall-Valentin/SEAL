import { z } from 'zod'
import { request } from '@/shared/api'
import type { IUsernameAvailabilityApi } from './IUsernameAvailabilityApi'

/** SEC-01 : réponse de username-availability (ADR 0019) ; trois suggestions au plus, identifiants d'AUTH-01. */
const AVAILABILITY = z.object({
  available: z.boolean(),
  suggestions: z.array(z.string().min(3).max(20)).max(3),
})

/** Adaptateur HTTP du port IUsernameAvailabilityApi. */
export const usernameAvailabilityApi: IUsernameAvailabilityApi = {
  usernameAvailability(username, signal) {
    return request(
      `/api/v1/auth/username-availability?u=${encodeURIComponent(username)}`,
      AVAILABILITY,
      signal ? { signal } : {},
    )
  },
}
