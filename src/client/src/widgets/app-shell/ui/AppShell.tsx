import type { ReactNode } from 'react'
import { Link, NavLink, Outlet, useLocation } from 'react-router'
import { initialsOf } from '@/entities/account'
import { useReceivedRequests } from '@/entities/friend'
import { useCurrentAccount } from '@/entities/session'
import { conversationIdOf, ROUTES, settingsSectionOf } from '@/shared/config'
import { useMessages } from '@/shared/i18n'
import { Avatar, PhoqueHead } from '@/shared/ui'
import { appShellMessages } from '../i18n'
import { useRealtimeUpdates } from '../model/useRealtimeUpdates'
import { ConversationsIcon, FriendsIcon, SettingsIcon } from './icons'

/**
 * Coque du compte actif (ADR 0035, point 3) : navigation principale de la maquette, en rail sur ordinateur (88 px,
 * libellés) et sur tablette (72 px, icônes), en barre du bas sur téléphone. Amis porte la pastille des invitations
 * reçues (FRD-04, ADR 0038) ; les non-lus des conversations attendent le lot 4 (ADR 0041, point 7).
 */
export function AppShell() {
  const location = useLocation()
  // TRN-10, ADR 0043 : une seule connexion temps réel, pour tous les écrans du compte actif.
  useRealtimeUpdates()
  const params = new URLSearchParams(location.search)
  // Parametres-Telephone-Preferences : une sous-page des paramètres occupe tout l'écran, sans la barre du bas ;
  // Conv-Telephone-Fil de même pour un fil ouvert (ADR 0041, point 1).
  const fullScreen =
    (location.pathname === ROUTES.SETTINGS && settingsSectionOf(params) !== null) ||
    (location.pathname === ROUTES.HOME && conversationIdOf(params) !== null)
  return (
    <div className="flex min-h-screen flex-col bg-bg font-sans text-ink sm:flex-row">
      <Rail />
      <div className="flex min-w-0 flex-1 flex-col">
        <Outlet />
      </div>
      {!fullScreen && <BottomBar />}
    </div>
  )
}

const DESTINATIONS = [
  {
    to: ROUTES.HOME,
    label: 'conversations',
    icon: <ConversationsIcon />,
    end: true,
    counted: false,
  },
  { to: ROUTES.FRIENDS, label: 'friends', icon: <FriendsIcon />, end: false, counted: true },
  { to: ROUTES.SETTINGS, label: 'settings', icon: <SettingsIcon />, end: false, counted: false },
] as const

/** FRD-04 : nombre affiché sur la pastille, plafonné à « 99+ » ; rien sans invitation. */
function badgeOf(count: number): string | null {
  return count === 0 ? null : count > 99 ? '99+' : String(count)
}

/** Invitations reçues en attente et nom accessible de l'entrée Amis (« Amis, 2 invitations »). */
function useFriendsEntry() {
  const t = useMessages(appShellMessages)
  const count = useReceivedRequests().data?.length ?? 0
  const name =
    count === 0
      ? t('friends')
      : count === 1
        ? t('friendsOne')
        : t('friendsMany', { count: String(count) })
  return { badge: badgeOf(count), name }
}

const BADGE =
  'absolute grid h-5 min-w-5 place-items-center rounded-full bg-accent px-1.5 text-[11px] font-bold text-on-accent'

/** Ordinateur et tablette : rail vertical à gauche. */
function Rail() {
  const t = useMessages(appShellMessages)
  const friends = useFriendsEntry()
  return (
    <nav
      aria-label={t('mainNav')}
      className="sticky top-0 hidden h-screen w-18 flex-none flex-col items-center gap-2 border-r border-line bg-sunken pt-3.5 pb-4 sm:flex lg:w-22"
    >
      <Link
        to={ROUTES.HOME}
        aria-label={t('home')}
        className="mb-3 grid size-12 place-items-center rounded-[14px]"
      >
        <span className="lg:hidden">
          <PhoqueHead size={32} />
        </span>
        <span className="hidden lg:block">
          <PhoqueHead size={34} />
        </span>
      </Link>
      <div className="flex flex-1 flex-col gap-2 lg:gap-1.5">
        {DESTINATIONS.map(({ counted, ...destination }) => (
          <RailLink
            key={destination.to}
            {...destination}
            text={t(destination.label)}
            name={counted ? friends.name : t(destination.label)}
            badge={counted ? friends.badge : null}
          />
        ))}
      </div>
      <ProfileLink />
    </nav>
  )
}

interface DestinationProps {
  readonly to: string
  /** Libellé visible (ordinateur, barre du bas). */
  readonly text: string
  /** Nom accessible : le libellé, et les invitations pour Amis. */
  readonly name: string
  readonly icon: ReactNode
  readonly end: boolean
  readonly badge: string | null
}

function RailLink({ to, text, name, icon, end, badge }: DestinationProps) {
  return (
    <NavLink
      to={to}
      end={end}
      aria-label={name}
      className={({ isActive }) =>
        `relative grid size-13 place-items-center rounded-[14px] no-underline lg:flex lg:h-auto lg:min-h-15 lg:w-19 lg:flex-col lg:items-center lg:justify-center lg:gap-1 lg:text-[10.5px] lg:font-semibold ${
          isActive ? 'bg-accent-soft-2 text-accent-text lg:bg-accent-soft' : 'text-ink-3'
        }`
      }
    >
      {icon}
      <span aria-hidden="true" className="hidden lg:inline">
        {text}
      </span>
      {badge !== null && (
        <span aria-hidden="true" className={`${BADGE} top-0.5 right-0 lg:top-1 lg:right-3.5`}>
          {badge}
        </span>
      )}
    </NavLink>
  )
}

/** « Mon profil » : les initiales du nom affiché, vers Paramètres. */
function ProfileLink() {
  const t = useMessages(appShellMessages)
  const account = useCurrentAccount().data
  return (
    <Link
      to={ROUTES.SETTINGS}
      aria-label={t('profile')}
      className="grid size-10 place-items-center rounded-full no-underline"
    >
      <Avatar initials={account == null ? '' : initialsOf(account.displayName)} size={40} />
    </Link>
  )
}

/** Téléphone : barre du bas, une colonne par destination. */
function BottomBar() {
  const t = useMessages(appShellMessages)
  const friends = useFriendsEntry()
  return (
    <nav
      aria-label={t('mainNav')}
      className="sticky bottom-0 grid h-16 flex-none grid-cols-3 border-t border-line bg-surface sm:hidden"
    >
      {DESTINATIONS.map(({ to, label, icon, end, counted }) => (
        <NavLink
          key={to}
          to={to}
          end={end}
          aria-label={counted && friends.badge !== null ? friends.name : undefined}
          className={({ isActive }) =>
            `group flex flex-col items-center justify-center gap-0.75 text-[11px] font-semibold no-underline ${
              isActive ? 'text-accent-text' : 'text-ink-3'
            }`
          }
        >
          <span className="relative grid h-7.5 w-14 place-items-center rounded-full group-aria-[current=page]:bg-accent-soft">
            {icon}
            {counted && friends.badge !== null && (
              <span aria-hidden="true" className={`${BADGE} -top-1 right-1.5`}>
                {friends.badge}
              </span>
            )}
          </span>
          {t(label)}
        </NavLink>
      ))}
    </nav>
  )
}
