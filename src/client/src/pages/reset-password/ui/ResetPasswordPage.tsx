import { useEffect, useRef } from 'react'
import { useCurrentAccount } from '@/entities/session'
import { LogoutButton } from '@/features/logout'
import { NewPasswordPanel, useResetLink } from '@/features/password-reset'
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
  const link = useResetLink()
  const account = useCurrentAccount()
  const connected = account.data !== undefined && account.data !== null
  // ADR 0024, point 10 : après « Se déconnecter », le focus va au formulaire qui remplace le message.
  const content = useRef<HTMLDivElement>(null)
  const wasConnected = useRef(false)
  useEffect(() => {
    if (wasConnected.current && !connected) {
      content.current?.focus()
    }
    wasConnected.current = connected
  }, [connected])
  if (account.isPending) {
    return null
  }
  return (
    <AuthLayout variant="neutral">
      <div ref={content} tabIndex={-1} className="flex flex-col gap-4.5 outline-none sm:gap-5">
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
          <NewPasswordPanel key={link.linkId} token={link.token} linkId={link.linkId} />
        )}
      </div>
    </AuthLayout>
  )
}
