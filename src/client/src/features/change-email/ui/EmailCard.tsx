import { useId, useState } from 'react'
import type { Account } from '@/entities/session'
import { useMessages } from '@/shared/i18n'
import { emailChangeMessages } from '../i18n'
import { ChangeEmailDialog } from './ChangeEmailDialog'

interface EmailCardProps {
  readonly account: Account
  /** 403 REAUTH_REQUIRED : l'écran ouvre la ré-authentification, puis appelle retry (ADR 0035). */
  readonly onReauthRequired: (retry: () => void) => void
}

/**
 * ACC-02 : carte Courriel de Parametres-Ordinateur, qui ouvre le dialogue ; la réussite est annoncée. Sur téléphone
 * (non dessiné), le bouton passe sous l'adresse, et la carte prend la marge de Parametres-Telephone-Preferences.
 */
export function EmailCard({ account, onReauthRequired }: EmailCardProps) {
  const t = useMessages(emailChangeMessages)
  const titleId = useId()
  const [open, setOpen] = useState(false)
  const [changed, setChanged] = useState(false)
  return (
    <section
      aria-labelledby={titleId}
      className="flex flex-col rounded-[18px] bg-surface p-4 inset-ring inset-ring-line sm:p-6"
    >
      <div className="flex flex-col items-start gap-3 sm:flex-row sm:justify-between sm:gap-4">
        <div className="min-w-0">
          <h3 id={titleId} className="m-0 text-[16px] font-bold">
            {t('title')}
          </h3>
          <p className="mt-1 mb-0 font-mono text-[13px] wrap-anywhere text-ink-3">
            {account.email}
          </p>
        </div>
        <button
          type="button"
          className="h-9 flex-none rounded-[8px] bg-surface px-3 font-sans text-[14px] font-semibold text-ink inset-ring inset-ring-line-strong"
          onClick={() => {
            setChanged(false)
            setOpen(true)
          }}
        >
          {t('open')}
        </button>
      </div>
      <span role="status" className="mt-3 text-[13px] font-semibold text-accent-text empty:mt-0">
        {changed ? t('changed') : ''}
      </span>
      <ChangeEmailDialog
        open={open}
        account={account}
        onReauthRequired={onReauthRequired}
        onChanged={() => {
          setOpen(false)
          setChanged(true)
        }}
        onCancel={() => {
          setOpen(false)
        }}
      />
    </section>
  )
}
