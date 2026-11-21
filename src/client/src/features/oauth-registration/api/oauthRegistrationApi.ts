import { z } from 'zod'
import { DISPLAY_NAME_SCHEMA, EMAIL_SCHEMA } from '@/entities/account'
import { OAUTH_PROVIDER_IDS } from '@/entities/oauth-provider'
import { reloadCsrf, request, requestNoContent } from '@/shared/api'
import { ApiError } from '@/shared/lib'
import type { IOAuthRegistrationApi } from './IOAuthRegistrationApi'

/**
 * SEC-01 : réponse de GET /api/v1/auth/oauth/pending (ADR 0032), validée avec les règles du serveur. Un courriel mal
 * formé refuse la réponse. Un nom proposé hors de la règle d'ACC-03 devient vide, comme le serveur le fait
 * (AUTH-30 : le nom ne refuse jamais l'identité) : une table Unicode plus ancienne dans le navigateur ne bloque pas
 * l'inscription, l'utilisateur saisit son nom.
 */
const PENDING = z.object({
  provider: z.enum(OAUTH_PROVIDER_IDS),
  email: EMAIL_SCHEMA,
  displayName: z.union([z.literal(''), DISPLAY_NAME_SCHEMA]).catch(''),
  expiresAt: z.iso.datetime(),
})

/** Adaptateur HTTP du port IOAuthRegistrationApi. */
export const oauthRegistrationApi: IOAuthRegistrationApi = {
  async pending(signal) {
    try {
      const pending = await request('/api/v1/auth/oauth/pending', PENDING, signal ? { signal } : {})
      return { provider: pending.provider, email: pending.email, displayName: pending.displayName }
    } catch (error) {
      if (error instanceof ApiError && error.status === 404) {
        return null
      }
      throw error
    }
  },
  async complete(registration) {
    await requestNoContent('/api/v1/auth/oauth/complete', {
      method: 'POST',
      body: { username: registration.username, displayName: registration.displayName },
    })
    // AUTH-60 : la session ouverte a perdu son jeton CSRF (ADR 0019). Un échec ne défait pas l'inscription réussie : la
    // première requête non sûre rechargera le jeton absent (shared/api).
    await reloadCsrf().catch(() => undefined)
  },
}
