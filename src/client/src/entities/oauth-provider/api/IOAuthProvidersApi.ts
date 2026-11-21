/**
 * AUTH-35 : fournisseurs OAuth que le client sait afficher. Microsoft arrive avec AUTH-34 (P1), son logo et sa mention
 * « compte UQAC » (ADR 0033).
 */
export const OAUTH_PROVIDER_IDS = ['google', 'github'] as const

export type OAuthProviderId = (typeof OAUTH_PROVIDER_IDS)[number]

/** Port des fournisseurs configurés (ADR 0033). */
export interface IOAuthProvidersApi {
  /** Les fournisseurs configurés, dans l'ordre des boutons ; aucun sans identifiants (OPS-03). */
  providers(signal?: AbortSignal): Promise<readonly OAuthProviderId[]>
}
