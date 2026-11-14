import { useEffect, useRef } from 'react'
import { EMAIL_MAX_LENGTH } from '@/entities/account'
import type { Account } from '@/entities/session'
import { useMessages } from '@/shared/i18n'
import { Alert, FormAlert, PrimaryButton, SecondaryButton, TextField } from '@/shared/ui'
import { verifyEmailMessages } from '../i18n'
import { formatCountdown, type VerifyFailure } from '../model/code'
import { useEmailVerification } from '../model/useEmailVerification'
import { CodeInput } from './CodeInput'

interface VerifyEmailPanelProps {
  readonly email: string
  readonly verification: Account['verification']
}

function FailureView({ failure }: { readonly failure: VerifyFailure }) {
  const t = useMessages(verifyEmailMessages)
  switch (failure.kind) {
    case 'invalid-code':
      return <Alert variant="error">{t('invalidCode')}</Alert>
    case 'expired':
      return <Alert variant="error">{t('expired')}</Alert>
    case 'rate-limited':
    case 'network':
    case 'unexpected':
      return <FormAlert failure={failure} />
  }
}

/** AUTH-14, AUTH-17 : code, renvoi et changement du courriel en attente (Verification-Courriel). */
export function VerifyEmailPanel({ email, verification }: VerifyEmailPanelProps) {
  const t = useMessages(verifyEmailMessages)
  const boxes = useRef<(HTMLInputElement | null)[]>([])
  const emailField = useRef<HTMLInputElement>(null)
  const changeLink = useRef<HTMLButtonElement>(null)
  const panel = useEmailVerification({ email, verification }, () => {
    boxes.current[0]?.focus()
  })
  // ADR 0024, point 10 : le focus suit le champ « Courriel » ouvert sur place, puis revient au lien qui l'a ouvert.
  const wasChanging = useRef(false)
  useEffect(() => {
    if (panel.changing) {
      emailField.current?.focus()
    } else if (wasChanging.current) {
      changeLink.current?.focus()
    }
    wasChanging.current = panel.changing
  }, [panel.changing])
  const remaining = panel.resendsRemaining
  return (
    <>
      {panel.alert !== null && <FailureView failure={panel.alert} />}
      <form
        className="flex flex-col gap-3.5"
        noValidate
        onSubmit={(event) => {
          event.preventDefault()
          panel.verify()
        }}
      >
        <CodeInput
          cells={panel.cells}
          onChange={panel.setCells}
          disabled={panel.codeLocked}
          boxes={boxes}
        />
        <div className="flex justify-between gap-3 text-[13px] text-ink-3">
          <span>
            {t('expiresIn')}{' '}
            <span className="font-mono text-ink-2 tabular-nums">
              {formatCountdown(panel.secondsLeft)}
            </span>
          </span>
          <span>{t('attempts')}</span>
        </div>
        <PrimaryButton disabled={!panel.canVerify}>{t('submit')}</PrimaryButton>
      </form>
      <div className="flex items-center justify-between gap-3">
        <button
          type="button"
          disabled={!panel.canResend}
          className="-ml-3 inline-flex min-h-11 items-center gap-2 rounded-[10px] px-3 font-sans text-[14px] font-semibold text-accent-text disabled:opacity-55"
          onClick={panel.resend}
        >
          <svg
            width="18"
            height="18"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="1.7"
            strokeLinecap="round"
            strokeLinejoin="round"
            aria-hidden="true"
          >
            <path d="M19.5 12a7.5 7.5 0 1 1-2.2-5.3M19.5 4.5V9H15" />
          </svg>
          {t('resend')}
        </button>
        <span className="text-[13px] text-ink-3">
          {remaining === 0
            ? t('resendsNone')
            : remaining === 1
              ? t('resendsOne')
              : t('resendsMany', { count: remaining })}
        </span>
      </div>
      <p role="status" className="m-0 text-[13px] text-ink-2 empty:hidden">
        {panel.sentTo !== null && t('sent', { email: panel.sentTo })}
      </p>
      <p className="m-0 text-[13px] leading-[1.4] text-ink-3">
        {t('inactive')}{' '}
        {!panel.changing && (
          <button
            ref={changeLink}
            type="button"
            className="font-semibold text-accent-text hover:text-accent-hover"
            onClick={panel.openChange}
          >
            {t('change')}
          </button>
        )}
      </p>
      {panel.changing && (
        <form
          className="flex flex-col gap-3.5"
          noValidate
          onSubmit={(event) => {
            event.preventDefault()
            panel.changeEmail()
          }}
        >
          <TextField
            label={t('email')}
            type="email"
            autoComplete="email"
            maxLength={EMAIL_MAX_LENGTH}
            value={panel.newEmail}
            onChange={panel.setNewEmail}
            onBlur={panel.touchNewEmail}
            inputRef={emailField}
            error={panel.newEmailInvalid ? t('invalidEmail') : null}
          />
          <div className="flex gap-2">
            <div className="flex flex-1 flex-col">
              <PrimaryButton disabled={!panel.canChange}>{t('sendCode')}</PrimaryButton>
            </div>
            <SecondaryButton onClick={panel.cancelChange}>{t('cancel')}</SecondaryButton>
          </div>
        </form>
      )}
    </>
  )
}
