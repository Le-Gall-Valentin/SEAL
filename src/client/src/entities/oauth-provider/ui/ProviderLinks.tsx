import { useMessages } from '@/shared/i18n'
import { oauthProviderMessages } from '../i18n'
import type { OAuthProviderId } from '../model/oauthProvider'
import { useOAuthProviderName } from '../model/useOAuthProviderName'
import { ProviderLogo } from './ProviderLogo'

/** « ou », entre un formulaire et les boutons des fournisseurs (Connexion-*, Inscription-Telephone ; ADR 0035). */
export function OrSeparator() {
  const t = useMessages(oauthProviderMessages)
  return (
    <div className="flex items-center gap-3 text-[13px] text-ink-3">
      <span className="h-px flex-1 bg-line" />
      {t('or')}
      <span className="h-px flex-1 bg-line" />
    </div>
  )
}

/**
 * AUTH-30, AUTH-55 : un lien « Continuer avec … » par fournisseur, vers son départ OAuth. Un lien, et non un bouton :
 * une navigation de premier niveau, sur la même origine, que Fetch Metadata attend (ADR 0032, point 3 ; ADR 0033).
 * Les fournisseurs viennent d'un ensemble fermé, jamais d'un texte du serveur.
 */
export function ProviderLinks({ providers }: { readonly providers: readonly OAuthProviderId[] }) {
  const t = useMessages(oauthProviderMessages)
  const name = useOAuthProviderName()
  return (
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
  )
}
