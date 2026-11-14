import { Link } from 'react-router'
import { ForgotPasswordForm } from '@/features/password-reset'
import { ROUTES } from '@/shared/config'
import { useMessages } from '@/shared/i18n'
import { AuthLayout } from '@/widgets/auth-layout'
import { forgotPasswordPageMessages } from '../i18n'

/** Écran Mot-de-passe-oublie (AUTH-70), réservé aux anonymes. */
export function ForgotPasswordPage() {
  const t = useMessages(forgotPasswordPageMessages)
  return (
    <AuthLayout variant="searching">
      <div className="flex flex-col gap-4.5 sm:gap-5">
        <header className="flex flex-col gap-1.5">
          <h1 className="m-0 text-[26px] leading-[1.15] font-bold tracking-[-0.02em]">
            {t('title')}
          </h1>
          <p className="m-0 leading-[1.45] text-ink-3">{t('subtitle')}</p>
        </header>
        <ForgotPasswordForm />
        <p className="m-0 text-center text-[13px]">
          <Link to={ROUTES.LOGIN} className="font-semibold no-underline">
            {t('back')}
          </Link>
        </p>
      </div>
    </AuthLayout>
  )
}
