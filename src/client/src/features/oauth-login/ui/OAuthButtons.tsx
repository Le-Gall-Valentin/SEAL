import { OrSeparator, ProviderLinks, useOAuthProviders } from '@/entities/oauth-provider'

/**
 * AUTH-30, AUTH-35, OPS-03 : « ou », puis un bouton par fournisseur configuré ; rien sans fournisseur (ADR 0024,
 * point 3). Chaque bouton est un lien : une navigation de premier niveau, sur la même origine, que Fetch Metadata
 * attend (ADR 0032, point 3 ; ADR 0033).
 */
export function OAuthButtons() {
  const providers = useOAuthProviders()
  if (providers.length === 0) {
    return null
  }
  return (
    <>
      <OrSeparator />
      <ProviderLinks providers={providers} />
    </>
  )
}
