import { useMessages } from '@/shared/i18n'
import type { OAuthProviderId } from '../api/IOAuthProvidersApi'
import { oauthProviderMessages } from '../i18n'

/** Nom affiché d'un fournisseur (« Google », « GitHub »). */
export function useOAuthProviderName(): (provider: OAuthProviderId) => string {
  const t = useMessages(oauthProviderMessages)
  return (provider) => t(provider)
}
