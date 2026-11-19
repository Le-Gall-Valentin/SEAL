import { LogoutButton } from '@/features/logout'
import { useMessages } from '@/shared/i18n'
import { StatePage } from '@/shared/ui'
import { keysWaitingPageMessages } from '../i18n'

/**
 * KEY-02, ADR 0029 (point 13) : un autre onglet tient le verrou des clés (dialogue de la clé ouvert) ; cet onglet
 * l'attend, sans écran du compte, et peut se déconnecter. Il passe à la zone du compte dès que le verrou est rendu.
 */
export function KeysWaitingPage() {
  const t = useMessages(keysWaitingPageMessages)
  return (
    <StatePage>
      <section className="flex w-full max-w-95 flex-col items-start gap-4 rounded-[18px] bg-surface p-5.5 inset-ring inset-ring-line">
        <p role="status" className="m-0 text-[14px] leading-[1.45] text-ink-2">
          {t('text')}
        </p>
        <LogoutButton variant="outlined" />
      </section>
    </StatePage>
  )
}
