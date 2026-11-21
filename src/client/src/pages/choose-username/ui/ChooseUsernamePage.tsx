import { Navigate } from 'react-router'
import { useOAuthProviderName } from '@/entities/oauth-provider'
import { OAuthRegistrationForm, usePendingRegistration } from '@/features/oauth-registration'
import { ROUTES } from '@/shared/config'
import { useMessages } from '@/shared/i18n'
import { LoadErrorState } from '@/shared/ui'
import { AuthLayout } from '@/widgets/auth-layout'
import { chooseUsernamePageMessages } from '../i18n'

/**
 * Écran Identifiant-OAuth (AUTH-32), réservé aux anonymes, sur la mise en page d'Inscription hors téléphone (ADR 0033).
 * Sans inscription en attente, il ramène à la connexion avec REQUEST_REJECTED (ADR 0033, point 2).
 */
export function ChooseUsernamePage() {
  const t = useMessages(chooseUsernamePageMessages)
  const providerName = useOAuthProviderName()
  const pending = usePendingRegistration()
  if (pending.isPending) {
    return null
  }
  if (pending.isError) {
    return (
      <AuthLayout variant="waving">
        <LoadErrorState
          heading="h1"
          onRetry={() => {
            void pending.refetch()
          }}
        />
      </AuthLayout>
    )
  }
  if (pending.data === null) {
    return <Navigate to={`${ROUTES.LOGIN}?erreur=REQUEST_REJECTED`} replace />
  }
  return (
    <AuthLayout variant="waving">
      <div className="flex flex-col gap-4.5 sm:gap-5">
        <header className="flex flex-col gap-1.5">
          <h1 className="m-0 text-[26px] leading-[1.15] font-bold tracking-[-0.02em] text-balance">
            {t('title')}
          </h1>
          <p className="m-0 leading-[1.45] text-ink-3">
            {t('arrivedFrom', { provider: providerName(pending.data.provider) })}{' '}
            <span className="font-semibold wrap-anywhere text-ink-2">{pending.data.email}</span>
            {t('rest')}
          </p>
        </header>
        <OAuthRegistrationForm pending={pending.data} />
      </div>
    </AuthLayout>
  )
}
