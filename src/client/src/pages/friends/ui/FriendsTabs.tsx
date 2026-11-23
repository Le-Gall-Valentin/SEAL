import { Link } from 'react-router'
import { useMessages } from '@/shared/i18n'
import { FRIENDS_INVITATIONS, type FriendsTab, ROUTES } from '@/shared/config'
import { friendsPageMessages } from '../i18n'

interface FriendsTabsProps {
  readonly tab: FriendsTab
  readonly friendCount: number | undefined
  readonly receivedCount: number
}

/** Onglets « Amis » et « Invitations » d'Amis-Ordinateur : des liens, `aria-current` sur l'onglet ouvert. */
export function FriendsTabs({ tab, friendCount, receivedCount }: FriendsTabsProps) {
  const t = useMessages(friendsPageMessages)
  const pending =
    receivedCount === 1
      ? t('oneInvitationPending')
      : t('invitationsPending', { count: String(receivedCount) })
  return (
    <nav aria-label={t('sections')} className="flex gap-1 border-b border-line">
      <TabLink to={ROUTES.FRIENDS} current={tab === 'amis'} label={t('tabFriends')}>
        {friendCount !== undefined && (
          <span className="grid h-5 min-w-5 place-items-center rounded-full bg-sunken px-1.5 text-[11px] font-bold text-ink-2 inset-ring inset-ring-line">
            {friendCount}
          </span>
        )}
      </TabLink>
      <TabLink to={FRIENDS_INVITATIONS} current={tab === 'invitations'} label={t('tabInvitations')}>
        {receivedCount > 0 && (
          <span
            aria-label={pending}
            className="grid h-5 min-w-5 place-items-center rounded-full bg-accent px-1.5 text-[11px] font-bold text-on-accent"
          >
            {receivedCount}
          </span>
        )}
      </TabLink>
    </nav>
  )
}

interface TabLinkProps {
  readonly to: string
  readonly current: boolean
  readonly label: string
  readonly children: React.ReactNode
}

function TabLink({ to, current, label, children }: TabLinkProps) {
  return (
    <Link
      to={to}
      aria-current={current ? 'page' : undefined}
      className={`relative inline-flex min-h-11 items-center gap-2 px-3.5 font-semibold no-underline ${current ? 'text-ink' : 'text-ink-3 hover:text-ink-2'}`}
    >
      {label}
      {children}
      {current && (
        <span
          aria-hidden="true"
          className="absolute right-2 -bottom-px left-2 h-0.5 rounded-xs bg-accent"
        />
      )}
    </Link>
  )
}
