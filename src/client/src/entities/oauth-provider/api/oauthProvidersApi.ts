import { z } from 'zod'
import { request } from '@/shared/api'
import { type IOAuthProvidersApi, OAUTH_PROVIDER_IDS } from './IOAuthProvidersApi'

/** SEC-01 : réponse de GET /api/v1/auth/providers (ADR 0032) ; un fournisseur inconnu ou répété refuse la réponse. */
const PROVIDERS = z.object({
  providers: z
    .array(z.enum(OAUTH_PROVIDER_IDS))
    .max(OAUTH_PROVIDER_IDS.length)
    .refine((providers) => new Set(providers).size === providers.length),
})

/** Adaptateur HTTP du port IOAuthProvidersApi. */
export const oauthProvidersApi: IOAuthProvidersApi = {
  async providers(signal) {
    const response = await request('/api/v1/auth/providers', PROVIDERS, signal ? { signal } : {})
    return response.providers
  },
}
