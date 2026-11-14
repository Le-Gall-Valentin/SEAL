import { useCurrentAccount } from '@/entities/session'
import { LogoutButton } from '@/features/logout'
import { NewPasswordPanel, useResetToken } from '@/features/password-reset'
import { useMessages } from '@/shared/i18n'
import { AuthLayout } from '@/widgets/auth-layout'
import { resetPasswordPageMessages } from '../i18n'

/**
 * Écran Nouveau-Mot-de-passe (AUTH-70, ADR 0022), ouvert par le lien du courriel. Les routes de réinitialisation sont
 * réservées aux anonymes : un navigateur connecté se déconnecte d'abord, et le jeton, lu à l'ouverture, reste en
 * mémoire (ADR 0024, point 9).
 */
export function ResetPasswordPage() {
  const t = useMessages(resetPasswordPageMessages)
  const token = useResetToken()
  const account = useCurrentAccount()
  if (account.isPending) {
    return null
  }
  const connected = account.data !== undefined && account.data !== null
  return (
    <AuthLayout variant="neutral">
      <div className="flex flex-col gap-4.5 sm:gap-5">
        {connected ? (
          <>
            <header className="flex flex-col gap-1.5">
              <h1 className="m-0 text-[26px] leading-[1.15] font-bold tracking-[-0.02em]">
                {t('title')}
              </h1>
              <p className="m-0 leading-[1.45] text-ink-3">{t('connected')}</p>
            </header>
            <LogoutButton />
          </>
        ) : (
          <NewPasswordPanel token={token} />
        )}
      </div>
    </AuthLayout>
  )
}
