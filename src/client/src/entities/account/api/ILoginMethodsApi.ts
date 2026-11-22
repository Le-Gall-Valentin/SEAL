import type { OAuthProviderId } from '@/entities/oauth-provider/@x/account'

/**
 * Méthodes de connexion du compte (ADR 0035, point 2) : un mot de passe ou non, et les fournisseurs liés, dans l'ordre
 * des boutons. La ré-authentification n'offre que celles-ci (AUTH-55).
 */
export interface LoginMethods {
  readonly passwordSet: boolean
  readonly providers: readonly OAuthProviderId[]
}

/** Port de GET /api/v1/account/identities. */
export interface ILoginMethodsApi {
  loginMethods(signal?: AbortSignal): Promise<LoginMethods>
}
