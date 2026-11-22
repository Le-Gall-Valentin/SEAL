/**
 * AUTH-35 : fournisseurs OAuth que le client sait afficher. Microsoft arrive avec AUTH-34 (P1), son logo et sa mention
 * « compte UQAC » (ADR 0033).
 */
export const OAUTH_PROVIDER_IDS = ['google', 'github'] as const

export type OAuthProviderId = (typeof OAUTH_PROVIDER_IDS)[number]

/**
 * Fournisseurs de la section 5.4 du CDC auxquels un compte peut être lié, affichés ou non par le client : un compte lié
 * à Microsoft reste valable avant AUTH-34 (ADR 0035, point 2).
 */
export const LINKABLE_OAUTH_PROVIDERS = ['microsoft', 'google', 'github'] as const

/** Port des fournisseurs configurés (ADR 0033). */
export interface IOAuthProvidersApi {
  /** Les fournisseurs configurés, dans l'ordre des boutons ; aucun sans identifiants (OPS-03). */
  providers(signal?: AbortSignal): Promise<readonly OAuthProviderId[]>
}
