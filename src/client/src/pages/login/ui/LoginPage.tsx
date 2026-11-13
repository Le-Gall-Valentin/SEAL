import { Link } from 'react-router'
import { LoginForm } from '@/features/login'
import { ROUTES } from '@/shared/config'
import { useMessages } from '@/shared/i18n'
import { AuthLayout } from '@/widgets/auth-layout'
import { loginPageMessages } from '../i18n'

/** Écran Connexion (AUTH-20) ; les boutons OAuth arrivent au lot 2 (ADR 0024, point 3). */
export function LoginPage() {
  const t = useMessages(loginPageMessages)
  return (
    <AuthLayout variant="waving">
      <div className="flex flex-col gap-4.5 sm:gap-5">
        <header className="flex flex-col gap-1 sm:gap-1.5">
          <h1 className="m-0 text-[26px] font-bold tracking-[-0.02em]">{t('title')}</h1>
          <p className="m-0 text-ink-3">{t('subtitle')}</p>
        </header>
        <LoginForm />
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
