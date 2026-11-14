import { useCurrentAccount } from '@/entities/session'
import { VerifyEmailPanel } from '@/features/verify-email'
import { useMessages } from '@/shared/i18n'
import { AuthLayout } from '@/widgets/auth-layout'
import { verifyEmailPageMessages } from '../i18n'

/**
 * Écran Verification-Courriel (AUTH-14), réservé au compte en attente : son adresse, l'expiration du code et les
 * renvois restants viennent de /me (AUTH-16).
 */
export function VerifyEmailPage() {
  const t = useMessages(verifyEmailPageMessages)
  const account = useCurrentAccount().data
  if (account === undefined || account === null) {
    return null
  }
  return (
    <AuthLayout variant="waving">
      <div className="flex flex-col gap-4.5 sm:gap-5">
        <header className="flex flex-col gap-1.5">
          <h1 className="m-0 text-[26px] leading-[1.15] font-bold tracking-[-0.02em]">
            {t('title')}
          </h1>
          <p className="m-0 leading-[1.45] text-ink-3">
            {t('instruction')}{' '}
            <span className="font-mono text-[14px] text-ink-2">{account.email}</span>
            {t('period')}
          </p>
        </header>
        <VerifyEmailPanel email={account.email} verification={account.verification} />
      </div>
    </AuthLayout>
  )
}
