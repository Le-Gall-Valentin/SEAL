import type { ReactNode } from 'react'
import { Link, NavLink, Outlet } from 'react-router'
import { initialsOf } from '@/entities/account'
import { useCurrentAccount } from '@/entities/session'
import { ROUTES } from '@/shared/config'
import { useMessages } from '@/shared/i18n'
import { PhoqueHead } from '@/shared/ui'
import { appShellMessages } from '../i18n'
import { ConversationsIcon, SettingsIcon } from './icons'

/**
 * Coque du compte actif (ADR 0035, point 3) : navigation principale de la maquette, en rail sur ordinateur (88 px,
 * libellés) et sur tablette (72 px, icônes), en barre du bas sur téléphone. Amis et les pastilles de non-lus
 * attendent le lot 3 : SEAL n'annonce pas une fonction absente (ADR 0024, point 3).
 */
export function AppShell() {
  return (
    <div className="flex min-h-screen flex-col bg-bg font-sans text-ink sm:flex-row">
      <Rail />
      <div className="flex min-w-0 flex-1 flex-col">
        <Outlet />
      </div>
      <BottomBar />
    </div>
  )
}

const DESTINATIONS = [
  { to: ROUTES.HOME, label: 'conversations', icon: <ConversationsIcon />, end: true },
  { to: ROUTES.SETTINGS, label: 'settings', icon: <SettingsIcon />, end: false },
] as const

/** Ordinateur et tablette : rail vertical à gauche. */
function Rail() {
  const t = useMessages(appShellMessages)
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
        {DESTINATIONS.map((destination) => (
          <RailLink key={destination.to} {...destination} label={t(destination.label)} />
        ))}
      </div>
      <ProfileLink />
    </nav>
  )
}

interface DestinationProps {
  readonly to: string
  readonly label: string
  readonly icon: ReactNode
  readonly end: boolean
}

function RailLink({ to, label, icon, end }: DestinationProps) {
  return (
    <NavLink
      to={to}
      end={end}
      aria-label={label}
      className={({ isActive }) =>
        `grid size-13 place-items-center rounded-[14px] no-underline lg:flex lg:h-auto lg:min-h-15 lg:w-19 lg:flex-col lg:items-center lg:justify-center lg:gap-1 lg:text-[10.5px] lg:font-semibold ${
          isActive
            ? 'bg-accent-soft-2 text-accent-text lg:bg-accent-soft'
            : 'text-ink-3 hover:text-ink-2'
        }`
      }
    >
      {icon}
      <span aria-hidden="true" className="hidden lg:inline">
        {label}
      </span>
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
      className="grid size-10 place-items-center rounded-full bg-av-moss text-[13px] font-semibold text-ink-2 no-underline"
    >
      <span aria-hidden="true">{account == null ? '' : initialsOf(account.displayName)}</span>
    </Link>
  )
}

/** Téléphone : barre du bas, une colonne par destination. */
function BottomBar() {
  const t = useMessages(appShellMessages)
  return (
    <nav
      aria-label={t('mainNav')}
      className="sticky bottom-0 grid h-16 flex-none grid-cols-2 border-t border-line bg-surface sm:hidden"
    >
      {DESTINATIONS.map(({ to, label, icon, end }) => (
        <NavLink
          key={to}
          to={to}
          end={end}
          className={({ isActive }) =>
            `group flex flex-col items-center justify-center gap-0.75 text-[11px] font-semibold no-underline ${
              isActive ? 'text-accent-text' : 'text-ink-3'
            }`
          }
        >
          <span className="grid h-7.5 w-14 place-items-center rounded-full group-aria-[current=page]:bg-accent-soft">
            {icon}
          </span>
          {t(label)}
        </NavLink>
      ))}
    </nav>
  )
}
