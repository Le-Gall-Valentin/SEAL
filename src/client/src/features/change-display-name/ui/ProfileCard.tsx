import { useId } from 'react'
import { DisplayNameField, initialsOf } from '@/entities/account'
import type { Account } from '@/entities/session'
import { useMessages } from '@/shared/i18n'
import { FormAlert } from '@/shared/ui'
import { displayNameMessages } from '../i18n'
import { useDisplayNameForm } from '../model/useDisplayNameForm'

function LockIcon({ size }: { readonly size: 14 | 18 }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={size === 14 ? 1.9 : 1.7}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      className="flex-none"
    >
      <rect x="5" y="10.5" width="14" height="10" rx="2.5" />
      <path d="M8 10.5v-3a4 4 0 0 1 8 0v3" />
    </svg>
  )
}

/**
 * ACC-03 : carte Profil de Parametres-Ordinateur : l'avatar à initiales, « Nom affiché », l'identifiant définitif en
 * lecture seule (AUTH-01), « Enregistrer ».
 */
export function ProfileCard({
  account,
  headingLevel = 3,
}: {
  readonly account: Account
  /** 2 sous le titre de la sous-page du téléphone, 3 sous « Compte » de l'ordinateur (ADR 0035). */
  readonly headingLevel?: 2 | 3
}) {
  const Heading = headingLevel === 2 ? 'h2' : 'h3'
  const t = useMessages(displayNameMessages)
  const form = useDisplayNameForm(account)
  const id = useId()
  return (
    <section
      aria-labelledby={`${id}-titre`}
      className="flex flex-col gap-4 rounded-[18px] bg-surface p-4 inset-ring inset-ring-line sm:p-6"
    >
      <Heading id={`${id}-titre`} className="m-0 text-[16px] font-bold">
        {t('profile')}
      </Heading>
      <div className="flex items-center gap-4">
        <span
          aria-hidden="true"
          className="grid size-16 flex-none place-items-center rounded-full bg-av-moss text-[23px] font-semibold text-ink-2"
        >
          {initialsOf(account.displayName)}
        </span>
        <span className="text-[13px] leading-[1.4] text-ink-3">{t('avatarNote')}</span>
      </div>
      <FormAlert failure={form.formFailure} />
      <form
        noValidate
        className="flex flex-col gap-3.5"
        onSubmit={(event) => {
          event.preventDefault()
          form.submit()
        }}
      >
        <DisplayNameField
          value={form.value}
          onChange={form.setValue}
          onBlur={form.touch}
          invalid={form.invalid}
          withHint={false}
          disabled={form.locked || form.pending}
        />
        <div className="flex flex-col gap-1.5">
          <label htmlFor={`${id}-identifiant`} className="text-[13px] font-semibold text-ink-2">
            {t('username')}
          </label>
          <div className="flex h-11 items-center gap-1.5 rounded-[10px] bg-sunken px-3 text-ink-3 inset-ring inset-ring-line">
            <span aria-hidden="true" className="font-mono text-[15px]">
              @
            </span>
            <input
              id={`${id}-identifiant`}
              value={account.username}
              readOnly
              aria-describedby={`${id}-note`}
              className="min-w-0 flex-1 border-0 bg-transparent font-mono text-[15px] tracking-[0.01em] text-ink outline-none"
            />
            <LockIcon size={18} />
          </div>
          <span id={`${id}-note`} className="flex items-center gap-1.5 text-[13px] text-ink-3">
            <LockIcon size={14} />
            {t('usernameLocked')}
          </span>
        </div>
        <div className="flex items-center justify-end gap-3">
          <span role="status" className="text-[13px] font-semibold text-accent-text">
            {form.saved ? t('saved') : ''}
          </span>
          <button
            type="submit"
            disabled={!form.canSubmit}
            className="h-11 rounded-[10px] bg-accent px-4.5 font-sans text-[14.5px] font-semibold text-on-accent disabled:opacity-55"
          >
            {t('save')}
          </button>
        </div>
      </form>
    </section>
  )
}
