import { z } from 'zod'
import { OAUTH_PROVIDER_IDS, type OAuthProviderId } from '@/entities/oauth-provider/@x/account'
import { request } from '@/shared/api'
import type { ILoginMethodsApi } from './ILoginMethodsApi'

/**
 * Fournisseurs de la section 5.4 du CDC que le serveur peut rendre : un compte lié à un fournisseur que le client
 * n'affiche pas encore (Microsoft, AUTH-34) reste valable (ADR 0035, point 2).
 */
const LINKABLE_PROVIDERS = ['microsoft', ...OAUTH_PROVIDER_IDS] as const

function isDisplayable(provider: string): provider is OAuthProviderId {
  return (OAUTH_PROVIDER_IDS as readonly string[]).includes(provider)
}

/**
 * SEC-01 : réponse de GET /api/v1/account/identities (ADR 0035, point 2), champs inconnus refusés ; un fournisseur
 * inconnu ou répété refuse la réponse.
 */
const LOGIN_METHODS = z.strictObject({
  passwordSet: z.boolean(),
  providers: z
    .array(z.strictObject({ provider: z.enum(LINKABLE_PROVIDERS) }))
    .max(LINKABLE_PROVIDERS.length)
    .refine(
      (providers) => new Set(providers.map(({ provider }) => provider)).size === providers.length,
    ),
})

/** Adaptateur HTTP du port ILoginMethodsApi. */
export const loginMethodsApi: ILoginMethodsApi = {
  async loginMethods(signal) {
    const response = await request(
      '/api/v1/account/identities',
      LOGIN_METHODS,
      signal ? { signal } : {},
    )
    return {
      passwordSet: response.passwordSet,
      // Seuls les fournisseurs que le client sait afficher sont proposés (OPS-03).
      providers: response.providers.map(({ provider }) => provider).filter(isDisplayable),
    }
  },
}
