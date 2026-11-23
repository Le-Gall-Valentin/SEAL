import { useState } from 'react'
import type { KeyOwner } from '@/entities/keyring'
import { KeyRestoreForm } from '@/features/key-restore'
import { LogoutButton } from '@/features/logout'
import { ReauthReturnAlert, useOAuthReturnError } from '@/features/reauthenticate'
import { useMessages } from '@/shared/i18n'
import { AuthLayout } from '@/widgets/auth-layout'
import { restoreKeysPageMessages } from '../i18n'
import { IdentityResetDialog } from './IdentityResetDialog'
import { LostRecoveryKey } from './LostRecoveryKey'

interface RestoreKeysPageProps {
  readonly owner: KeyOwner
  readonly onRestored: () => void
}

/**
 * KEY-33, KEY-34 : Restauration-Appareil. Sur ordinateur et tablette, la mise en page de la connexion (ADR 0024) ;
 * « Se déconnecter », puis « Clé de récupération perdue ? » ; une fois la réinitialisation confirmée, la page laisse la
 * place à Dialogue-Cle-Recuperation, seul sur le fond (ADR 0036). Un retour OAuth refusé y est annoncé : `/parametres`
 * mène ici tant que l'appareil n'a pas de clés (ADR 0036, point 4).
 */
export function RestoreKeysPage({ owner, onRestored }: RestoreKeysPageProps) {
  const t = useMessages(restoreKeysPageMessages)
  const returnError = useOAuthReturnError()
  const [resetting, setResetting] = useState(false)
  if (resetting) {
    return <IdentityResetDialog owner={owner} onDone={onRestored} />
  }
  return (
    <AuthLayout variant="searching">
      <div className="flex flex-col gap-4.5">
        <header className="flex flex-col gap-1.5">
          <h1 className="m-0 text-[26px] leading-[1.15] font-bold tracking-[-0.02em]">
            {t('title')}
          </h1>
          <p className="m-0 leading-[1.45] text-ink-3">{t('text')}</p>
        </header>
        {returnError !== null && <ReauthReturnAlert error={returnError} />}
        <KeyRestoreForm owner={owner} onRestored={onRestored} />
        <LogoutButton />
        <LostRecoveryKey
          onConfirmed={() => {
            setResetting(true)
          }}
        />
      </div>
    </AuthLayout>
  )
}
