import { LogoutButton } from '@/features/logout'
import { useMessages } from '@/shared/i18n'
import { StateCard, StatePage } from '@/shared/ui'
import { identityChangedPageMessages } from '../i18n'

/**
 * KEY-33, 16.1 : le certificat du serveur porte une autre clé d'identité que celle de l'appareil (substitution de clé,
 * ou réinitialisation faite ailleurs). L'application est bloquée, sans autre issue que la déconnexion, sur le modèle de
 * KEY-11 (ADR 0029, point 5).
 */
export function IdentityChangedPage() {
  const t = useMessages(identityChangedPageMessages)
  return (
    <StatePage>
      <StateCard
        heading="h1"
        title={t('title')}
        text={t('text')}
        actions={<LogoutButton variant="outlined" />}
      />
    </StatePage>
  )
}
