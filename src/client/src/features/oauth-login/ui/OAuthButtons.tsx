import { ProviderLogo, useOAuthProviderName, useOAuthProviders } from '@/entities/oauth-provider'
import { useMessages } from '@/shared/i18n'
import { oauthLoginMessages } from '../i18n'

/**
 * AUTH-30, AUTH-35, OPS-03 : « ou », puis un bouton par fournisseur configuré ; rien sans fournisseur (ADR 0024,
 * point 3). Chaque bouton est un lien : une navigation de premier niveau, sur la même origine, que Fetch Metadata
 * attend (ADR 0032, point 3 ; ADR 0033).
 */
export function OAuthButtons() {
  const t = useMessages(oauthLoginMessages)
  const providers = useOAuthProviders()
  const name = useOAuthProviderName()
  if (providers.length === 0) {
    return null
  }
  return (
    <>
      <div className="flex items-center gap-3 text-[13px] text-ink-3">
        <span className="h-px flex-1 bg-line" />
        {t('or')}
        <span className="h-px flex-1 bg-line" />
      </div>
      <div className="flex flex-col gap-2">
        {providers.map((provider) => (
          <a
            key={provider}
            href={`/oauth2/authorization/${provider}`}
            className="flex h-11 items-center gap-3 rounded-[10px] bg-surface pr-3.5 pl-2.5 font-sans text-[14.5px] font-semibold text-ink no-underline inset-ring inset-ring-line-strong"
          >
            <span aria-hidden="true" className="grid size-6 place-items-center">
              <ProviderLogo provider={provider} size={18} />
            </span>
            <span className="flex-1 text-left">
              {t('continueWith', { provider: name(provider) })}
            </span>
          </a>
        ))}
      </div>
    </>
  )
}
