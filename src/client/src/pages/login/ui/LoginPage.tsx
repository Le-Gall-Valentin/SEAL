import { useEffect, useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router'
import { LoginForm } from '@/features/login'
import { LOGIN_ERROR_PARAM, oauthLoginErrorOf } from '@/entities/oauth-provider'
import { OAuthButtons, OAuthLoginAlert } from '@/features/oauth-login'
import type { PasswordResetDone } from '@/features/password-reset'
import { ROUTES } from '@/shared/config'
import { useMessages } from '@/shared/i18n'
import { useMounted } from '@/shared/lib'
import { Notice } from '@/shared/ui'
import { AuthLayout } from '@/widgets/auth-layout'
import { loginPageMessages } from '../i18n'

/** État de navigation posé par Nouveau-Mot-de-passe après une réussite ; tout autre état est ignoré. */
function isPasswordResetDone(state: unknown): state is PasswordResetDone {
  return (
    typeof state === 'object' &&
    state !== null &&
    'passwordReset' in state &&
    state.passwordReset === true
  )
}

/** Écran Connexion (AUTH-20), avec les boutons des fournisseurs et l'alerte du retour OAuth (ADR 0033). */
export function LoginPage() {
  const t = useMessages(loginPageMessages)
  const location = useLocation()
  const navigate = useNavigate()
  // React Router type l'état d'historique en any : il n'est lu qu'à travers isPasswordResetDone, une seule fois.
  const historyState: unknown = location.state
  const [passwordSaved] = useState(() => isPasswordResetDone(historyState))
  // ADR 0033 (point 1) : le code du retour OAuth, lu une fois, retiré au premier envoi du formulaire.
  const [oauthError, setOAuthError] = useState(() => oauthLoginErrorOf(location.search))
  const mounted = useMounted()
  // Lus, la confirmation quitte l'historique et le code quitte l'adresse, en une navigation : un rechargement ou un
  // retour ne réaffiche ni l'une ni l'autre.
  useEffect(() => {
    const params = new URLSearchParams(location.search)
    const confirmation = isPasswordResetDone(historyState)
    if (confirmation || params.has(LOGIN_ERROR_PARAM)) {
      params.delete(LOGIN_ERROR_PARAM)
      void navigate(
        { pathname: location.pathname, search: params.toString() },
        { replace: true, state: confirmation ? null : historyState },
      )
    }
  }, [historyState, location.pathname, location.search, navigate])
  return (
    <AuthLayout variant="waving">
      <div className="flex flex-col gap-4.5 sm:gap-5">
        <header className="flex flex-col gap-1 sm:gap-1.5">
          <h1 className="m-0 text-[26px] font-bold tracking-[-0.02em]">{t('title')}</h1>
          <p className="m-0 text-ink-3">{t('subtitle')}</p>
        </header>
        <div role="status" className="empty:hidden">
          {passwordSaved && mounted && <Notice variant="success">{t('passwordSaved')}</Notice>}
        </div>
        {oauthError !== null && mounted && <OAuthLoginAlert error={oauthError} />}
        <LoginForm
          onSubmit={() => {
            setOAuthError(null)
          }}
        />
        <OAuthButtons />
        <p className="m-0 text-center text-[13px] text-ink-3">
          {t('noAccount')}{' '}
          <Link to={ROUTES.REGISTER} className="font-semibold no-underline">
            {t('createAccount')}
          </Link>
        </p>
      </div>
    </AuthLayout>
  )
}
