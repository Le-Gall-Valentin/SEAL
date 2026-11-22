import type { ReactNode } from 'react'
import { Link, useSearchParams } from 'react-router'
import { type Account, useCurrentAccount } from '@/entities/session'
import { initialsOf } from '@/entities/account'
import { LogoutButton } from '@/features/logout'
import { ROUTES } from '@/shared/config'
import { useMessages } from '@/shared/i18n'
import { settingsMessages } from '../i18n'
import { SECTION_PARAM, sectionOf } from '../model/section'
import { AccountIcon, ChevronLeft, ChevronRight } from './icons'

const ACCOUNT_SECTION = `${ROUTES.SETTINGS}?${SECTION_PARAM}=compte`

/**
 * Paramètres (ACC-01 à ACC-03, ADR 0035). Ordinateur et tablette : navigation des sections à gauche, section Compte à
 * droite. Téléphone : la liste des sections, puis la sous-page Compte en plein écran (Parametres-Telephone-Preferences).
 * Seule la section Compte existe en P0 (point 4).
 */
export function SettingsPage() {
  const account = useCurrentAccount().data
  const [params] = useSearchParams()
  if (account == null) {
    return null
  }
  const content = <AccountContent />
  return (
    <div className="flex min-h-0 flex-1">
      <SectionsNav />
      <main className="hidden min-w-0 flex-1 sm:block">
        <div className="flex flex-col gap-4 px-6 pt-6 pb-10 lg:max-w-176 lg:gap-5 lg:px-12 lg:pt-9 lg:pb-16">
          <SectionTitle level={2} />
          {content}
        </div>
      </main>
      <div className="flex min-w-0 flex-1 flex-col sm:hidden">
        {sectionOf(params) === 'compte' ? (
          <PhoneSubpage>{content}</PhoneSubpage>
        ) : (
          <PhoneList account={account} />
        )}
      </div>
    </div>
  )
}

/** Contenu de la section Compte, rempli par les tâches suivantes. */
function AccountContent() {
  return <div className="flex flex-col gap-4 lg:gap-5" />
}

function SectionTitle({ level }: { readonly level: 1 | 2 }) {
  const t = useMessages(settingsMessages)
  const Heading = level === 1 ? 'h1' : 'h2'
  return (
    <header className="flex min-h-10 items-center sm:min-h-11">
      <Heading className="m-0 text-[26px] font-bold tracking-[-0.025em] sm:tracking-[-0.02em]">
        {t('account')}
      </Heading>
    </header>
  )
}

/** Ordinateur (280 px) et tablette (232 px) : navigation des sections, et « Se déconnecter » en bas. */
function SectionsNav() {
  const t = useMessages(settingsMessages)
  return (
    <nav
      aria-label={t('sections')}
      className="hidden w-58 flex-none flex-col gap-3 border-r border-line bg-surface px-3 pt-6 pb-5 sm:flex lg:w-70 lg:px-4 lg:py-8"
    >
      <h1 className="m-0 flex min-h-11 items-center px-2 text-[20px] font-bold tracking-[-0.025em] lg:min-h-0">
        {t('title')}
      </h1>
      <div className="flex flex-col gap-0.5">
        <Link
          to={ROUTES.SETTINGS}
          aria-current="page"
          className="flex min-h-11 items-center gap-3 rounded-[10px] bg-accent-soft px-3 py-2.5 font-semibold text-accent-text no-underline"
        >
          <AccountIcon size={20} />
          {t('account')}
        </Link>
      </div>
      <div className="mt-auto lg:pt-4">
        <LogoutButton />
      </div>
    </nav>
  )
}

/** Téléphone : carte du profil (sans l'empreinte, KEY-52 en P1), liste des sections, « Se déconnecter ». */
function PhoneList({ account }: { readonly account: Account }) {
  const t = useMessages(settingsMessages)
  return (
    <main className="flex flex-col gap-3.5 px-4 pt-4 pb-6">
      <header className="flex min-h-11 items-center">
        <h1 className="m-0 text-[26px] font-bold tracking-[-0.025em]">{t('title')}</h1>
      </header>
      <Link
        to={ACCOUNT_SECTION}
        className="flex items-center gap-3.5 rounded-[18px] bg-surface py-3.5 pr-2.5 pl-3.5 text-ink no-underline inset-ring inset-ring-line"
      >
        <span
          aria-hidden="true"
          className="grid size-14 flex-none place-items-center rounded-full bg-av-moss text-[20px] font-semibold text-ink-2"
        >
          {initialsOf(account.displayName)}
        </span>
        <span className="flex min-w-0 flex-1 flex-col gap-0.75">
          <span className="truncate text-[17px] font-bold tracking-[-0.01em]">
            {account.displayName}
          </span>
          <span className="truncate font-mono text-[13px] text-ink-3">@{account.username}</span>
        </span>
        <ChevronRight />
      </Link>
      <nav aria-label={t('sections')} className="flex flex-col gap-3">
        <ul className="m-0 list-none rounded-[18px] bg-surface p-0 inset-ring inset-ring-line">
          <li>
            <Link
              to={ACCOUNT_SECTION}
              className="flex min-h-16 items-center gap-3 py-2.5 pr-2.5 pl-3.5 text-ink no-underline"
            >
              <span
                aria-hidden="true"
                className="grid size-9 flex-none place-items-center rounded-[10px] bg-sunken text-ink-2 inset-ring inset-ring-line"
              >
                <AccountIcon size={19} />
              </span>
              <span className="flex min-w-0 flex-1 flex-col gap-0.5">
                <span className="text-[15.5px] font-semibold">{t('account')}</span>
                <span className="text-[13px] leading-[1.3] text-ink-3">{t('accountSummary')}</span>
              </span>
              <ChevronRight />
            </Link>
          </li>
        </ul>
      </nav>
      <LogoutButton />
    </main>
  )
}

/** Téléphone : sous-page en plein écran, par-dessus la barre du bas, avec le retour « Paramètres ». */
function PhoneSubpage({ children }: { readonly children: ReactNode }) {
  const t = useMessages(settingsMessages)
  return (
    <div className="fixed inset-0 z-10 flex flex-col overflow-y-auto bg-bg">
      <div className="flex h-13 flex-none items-center px-2 py-1">
        <Link
          to={ROUTES.SETTINGS}
          className="inline-flex min-h-11 items-center gap-1 rounded-[10px] pr-3 pl-1.5 text-[15px] font-semibold text-accent-text no-underline"
        >
          <ChevronLeft />
          {t('back')}
        </Link>
      </div>
      <main className="flex flex-col gap-3.5 px-4 pt-1 pb-6">
        <SectionTitle level={1} />
        {children}
      </main>
    </div>
  )
}
