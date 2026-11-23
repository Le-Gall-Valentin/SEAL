import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { initialsOf, useLoginMethods } from '@/entities/account'
import { type Account, useCurrentAccount } from '@/entities/session'
import { ProfileCard } from '@/features/change-display-name'
import { EmailCard } from '@/features/change-email'
import { PasswordCard } from '@/features/change-password'
import { LogoutButton } from '@/features/logout'
import {
  ReauthDialog,
  type ReauthReturnError,
  ReauthReturnAlert,
  useOAuthReturnError,
} from '@/features/reauthenticate'
import {
  ROUTES,
  SETTINGS_ACCOUNT,
  SETTINGS_SECTION_PARAM,
  settingsSectionOf,
} from '@/shared/config'
import { useMessages } from '@/shared/i18n'
import { useMediaQuery, WIDE_SCREEN } from '@/shared/lib'
import { Avatar, LockIcon } from '@/shared/ui'
import { settingsMessages } from '../i18n'
import { AccountIcon, ChevronLeft, ChevronRight } from './icons'

/**
 * Paramètres (ACC-01 à ACC-03, ADR 0035). Ordinateur et tablette : navigation des sections à gauche, section Compte à
 * droite. Téléphone : la liste des sections, puis la sous-page Compte en plein écran (Parametres-Telephone-Preferences).
 * Seule la section Compte existe en P0 (point 4).
 *
 * La section Compte garde la même place dans l'arbre d'une disposition à l'autre : tourner un téléphone (390 ↔ 844 px)
 * ne ferme ni un dialogue ni une saisie. Passer sous 640 px en regardant Compte ouvre sa sous-page, pas la liste.
 */
export function SettingsPage() {
  const account = useCurrentAccount().data
  const wide = useMediaQuery(WIDE_SCREEN)
  const [params, setParams] = useSearchParams()
  const returnError = useOAuthReturnError()
  const subpage = settingsSectionOf(params) !== null
  // Largeur du rendu précédent ; passer sous 640 px en regardant Compte garde Compte le temps que l'adresse prenne
  // ?section=compte (réglage d'état pendant le rendu, comme le recommande React).
  const [lastWide, setLastWide] = useState(wide)
  const [keepAccount, setKeepAccount] = useState(false)
  if (lastWide !== wide) {
    setLastWide(wide)
    if (lastWide && !subpage) {
      setKeepAccount(true)
    }
  }
  if (keepAccount && subpage) {
    setKeepAccount(false)
  }
  useEffect(() => {
    if (keepAccount && !subpage) {
      setParams(
        (current) => {
          const next = new URLSearchParams(current)
          next.set(SETTINGS_SECTION_PARAM, 'compte')
          return next
        },
        { replace: true },
      )
    }
  }, [keepAccount, subpage, setParams])
  if (account == null) {
    return null
  }
  if (!wide && !subpage && !keepAccount) {
    return <PhoneList account={account} returnError={returnError} />
  }
  return (
    <div className="flex min-h-0 flex-1">
      {wide && <SectionsNav />}
      <div className="flex min-w-0 flex-1 flex-col">
        {!wide && <BackLink />}
        <main className="min-w-0 flex-1">
          <div
            className={
              wide
                ? 'flex flex-col gap-4 px-6 pt-6 pb-10 lg:max-w-176 lg:gap-5 lg:px-12 lg:pt-9 lg:pb-16'
                : 'flex flex-col gap-3.5 px-4 pt-1 pb-6'
            }
          >
            <SectionTitle level={wide ? 2 : 1} />
            {returnError !== null && <ReauthReturnAlert error={returnError} />}
            <AccountContent account={account} headingLevel={wide ? 3 : 2} />
          </div>
        </main>
      </div>
    </div>
  )
}

/**
 * Section Compte : Profil, Courriel, Mot de passe (ADR 0035, point 4 : « Comptes liés » attend ACC-04 ; un compte
 * sans mot de passe, ou dont les méthodes ne sont pas encore lues, ne voit pas la carte Mot de passe).
 */
function AccountContent({
  account,
  headingLevel,
}: {
  readonly account: Account
  readonly headingLevel: 2 | 3
}) {
  const methods = useLoginMethods()
  // AUTH-55, ADR 0035 : l'action refusée faute de mode sudo, relancée après un mot de passe accepté.
  const [pendingRetry, setPendingRetry] = useState<{ readonly retry: () => void } | null>(null)
  return (
    <div className="flex flex-col gap-4 lg:gap-5">
      <ProfileCard account={account} headingLevel={headingLevel} />
      <EmailCard
        account={account}
        headingLevel={headingLevel}
        onReauthRequired={(retry) => {
          setPendingRetry({ retry })
        }}
      />
      {methods.data?.passwordSet === true && (
        <PasswordCard account={account} headingLevel={headingLevel} />
      )}
      <ReauthDialog
        open={pendingRetry !== null}
        onReauthenticated={() => {
          pendingRetry?.retry()
          setPendingRetry(null)
        }}
        onCancel={() => {
          setPendingRetry(null)
        }}
      />
    </div>
  )
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
      className="flex w-58 flex-none flex-col gap-3 border-r border-line bg-surface px-3 pt-6 pb-5 lg:w-70 lg:px-4 lg:py-8"
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
function PhoneList({
  account,
  returnError,
}: {
  readonly account: Account
  readonly returnError: ReauthReturnError | null
}) {
  const t = useMessages(settingsMessages)
  return (
    <main className="flex flex-col gap-3.5 px-4 pt-4 pb-6">
      <header className="flex min-h-11 items-center">
        <h1 className="m-0 text-[26px] font-bold tracking-[-0.025em]">{t('title')}</h1>
      </header>
      {returnError !== null && <ReauthReturnAlert error={returnError} />}
      <Link
        to={SETTINGS_ACCOUNT}
        className="flex items-center gap-3.5 rounded-[18px] bg-surface py-3.5 pr-2.5 pl-3.5 text-ink no-underline inset-ring inset-ring-line"
      >
        <Avatar initials={initialsOf(account.displayName)} size={56} />
        <span className="flex min-w-0 flex-1 flex-col gap-0.75">
          <span className="truncate text-[17px] font-bold tracking-[-0.01em]">
            {account.displayName}
          </span>
          <span className="flex min-w-0 items-center gap-1.25 font-mono text-[13px] text-ink-3">
            <span className="truncate">@{account.username}</span>
            <LockIcon size={13} label={t('usernameFinal')} />
          </span>
        </span>
        <ChevronRight />
      </Link>
      <nav aria-label={t('sections')} className="flex flex-col gap-3">
        <ul className="m-0 list-none rounded-[18px] bg-surface p-0 inset-ring inset-ring-line">
          <li>
            <Link
              to={SETTINGS_ACCOUNT}
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

/** Téléphone : retour « Paramètres » de la sous-page (la coque y retire la barre du bas). */
function BackLink() {
  const t = useMessages(settingsMessages)
  return (
    <div className="flex h-13 flex-none items-center px-2 py-1">
      <Link
        to={ROUTES.SETTINGS}
        className="inline-flex min-h-11 items-center gap-1 rounded-[10px] pr-3 pl-1.5 text-[15px] font-semibold text-accent-text no-underline"
      >
        <ChevronLeft />
        {t('back')}
      </Link>
    </div>
  )
}
