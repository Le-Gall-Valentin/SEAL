import { Link } from 'react-router'
import { RegisterForm } from '@/features/register'
import { ROUTES } from '@/shared/config'
import { useMessages } from '@/shared/i18n'
import { AuthLayout } from '@/widgets/auth-layout'
import { registerPageMessages } from '../i18n'

/** Écran Inscription (AUTH-10), mise en page de la connexion sur ordinateur et tablette (ADR 0007, point 7). */
export function RegisterPage() {
  const t = useMessages(registerPageMessages)
  return (
    <AuthLayout variant="waving">
      <div className="flex flex-col gap-4.5 sm:gap-5">
        <header className="flex flex-col gap-1 sm:gap-1.5">
          <h1 className="m-0 text-[26px] font-bold tracking-[-0.02em]">{t('title')}</h1>
          <p className="m-0 text-ink-3">{t('subtitle')}</p>
        </header>
        <RegisterForm />
        <p className="m-0 text-center text-[13px] text-ink-3">
          {t('haveAccount')}{' '}
          <Link to={ROUTES.LOGIN} className="font-semibold no-underline">
            {t('login')}
          </Link>
        </p>
      </div>
    </AuthLayout>
  )
}
