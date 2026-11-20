import type { KeyOwner } from '@/entities/keyring'
import { KeyRestoreForm } from '@/features/key-restore'
import { LogoutButton } from '@/features/logout'
import { useMessages } from '@/shared/i18n'
import { AuthLayout } from '@/widgets/auth-layout'
import { restoreKeysPageMessages } from '../i18n'

interface RestoreKeysPageProps {
  readonly owner: KeyOwner
  readonly onRestored: () => void
}

/**
 * KEY-33 : Restauration-Appareil. Sur ordinateur et tablette, la mise en page de la connexion (ADR 0024) ; « Se
 * déconnecter » est la seule issue tant que KEY-34 n'ajoute pas « Clé de récupération perdue ? » (ADR 0029, point 6).
 */
export function RestoreKeysPage({ owner, onRestored }: RestoreKeysPageProps) {
  const t = useMessages(restoreKeysPageMessages)
  return (
    <AuthLayout variant="searching">
      <div className="flex flex-col gap-4.5">
        <header className="flex flex-col gap-1.5">
          <h1 className="m-0 text-[26px] leading-[1.15] font-bold tracking-[-0.02em]">
            {t('title')}
          </h1>
          <p className="m-0 leading-[1.45] text-ink-3">{t('text')}</p>
        </header>
        <KeyRestoreForm owner={owner} onRestored={onRestored} />
        <LogoutButton />
      </div>
    </AuthLayout>
  )
}
