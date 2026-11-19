import { LogoutButton } from '@/features/logout'
import { useMessages } from '@/shared/i18n'
import { StateCard, StatePage } from '@/shared/ui'
import { authorityChangedPageMessages } from '../i18n'

/**
 * KEY-11 : l'application est bloquée, sans autre issue que la déconnexion, pour ne pas laisser la session ouverte
 * derrière le blocage (ADR 0027, point 1).
 */
export function AuthorityChangedPage() {
  const t = useMessages(authorityChangedPageMessages)
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
