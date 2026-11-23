import { useId, useState } from 'react'
import type { Account } from '@/entities/session'
import { useMessages } from '@/shared/i18n'
import { passwordChangeMessages } from '../i18n'
import { ChangePasswordDialog } from './ChangePasswordDialog'

/** ACC-01 : carte Mot de passe de Parametres-Ordinateur, qui ouvre le dialogue ; la réussite est annoncée. */
export function PasswordCard({
  account,
  headingLevel = 3,
}: {
  readonly account: Account
  /** 2 sous le titre de la sous-page du téléphone, 3 sous « Compte » de l'ordinateur (ADR 0035). */
  readonly headingLevel?: 2 | 3
}) {
  const Heading = headingLevel === 2 ? 'h2' : 'h3'
  const t = useMessages(passwordChangeMessages)
  const titleId = useId()
  const [open, setOpen] = useState(false)
  const [changed, setChanged] = useState(false)
  return (
    <section
      aria-labelledby={titleId}
      className="flex flex-col rounded-[18px] bg-surface p-4 inset-ring inset-ring-line sm:p-6"
    >
      <div className="flex flex-col items-start gap-3 sm:flex-row sm:justify-between sm:gap-4">
        <div>
          <Heading id={titleId} className="m-0 text-[16px] font-bold">
            {t('title')}
          </Heading>
          <p className="mt-1 mb-0 text-[13px] leading-[1.35] text-ink-3">{t('description')}</p>
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
      <ChangePasswordDialog
        open={open}
        account={account}
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
