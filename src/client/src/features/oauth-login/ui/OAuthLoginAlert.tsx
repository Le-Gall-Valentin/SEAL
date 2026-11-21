import { useMessages } from '@/shared/i18n'
import { Alert } from '@/shared/ui'
import { oauthLoginMessages } from '../i18n'
import type { OAuthLoginError } from '../model/oauthLoginError'

/** ADR 0033 (point 1) : l'alerte d'un départ ou d'un retour OAuth refusé, comme « Trop de tentatives ». */
export function OAuthLoginAlert({ error }: { readonly error: OAuthLoginError }) {
  const t = useMessages(oauthLoginMessages)
  switch (error) {
    case 'OAUTH_EMAIL_IN_USE':
      return (
        <Alert variant="error" title={t('emailInUseTitle')}>
          {t('emailInUseBody')}
        </Alert>
      )
    case 'RATE_LIMITED':
      return (
        <Alert variant="rate-limited" title={t('rateLimitedTitle')}>
          {t('rateLimitedBody')}
        </Alert>
      )
    case 'REQUEST_REJECTED':
      return (
        <Alert variant="error" title={t('rejectedTitle')}>
          {t('rejectedBody')}
        </Alert>
      )
  }
}
