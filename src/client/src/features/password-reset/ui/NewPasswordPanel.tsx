import { type ReactNode, useId } from 'react'
import { Link } from 'react-router'
import { PasswordRulesList, StrengthMeter } from '@/entities/password'
import { ROUTES } from '@/shared/config'
import { useMessages } from '@/shared/i18n'
import { Alert, FailureAlert, PasswordField, PrimaryButton, RateLimitedAlert } from '@/shared/ui'
import { passwordResetMessages } from '../i18n'
import { useNewPassword } from '../model/useNewPassword'

/** SEC-01 : borne de PasswordResetConfirmRequest côté serveur, avant normalisation. */
const PASSWORD_INPUT_MAX = 512

function Title({ children }: { readonly children?: ReactNode }) {
  const t = useMessages(passwordResetMessages)
  return (
    <header className="flex flex-col gap-1.5">
      <h1 className="m-0 text-[26px] leading-[1.15] font-bold tracking-[-0.02em]">{t('title')}</h1>
      {children}
    </header>
  )
}

/** AUTH-70, ADR 0022 : Nouveau-Mot-de-passe, pour le compte du lien. */
export function NewPasswordPanel({ token }: { readonly token: string | null }) {
  const t = useMessages(passwordResetMessages)
  const panel = useNewPassword(token)
  const strengthId = useId()
  if (panel.state === 'dead') {
    return (
      <>
        <Title />
        <Alert variant="error">{t('deadLink')}</Alert>
        <p className="m-0 text-center text-[13px]">
          <Link to={ROUTES.FORGOT_PASSWORD} className="font-semibold no-underline">
            {t('requestNew')}
          </Link>
        </p>
      </>
    )
  }
  if (panel.state === 'error' && panel.linkFailure !== null) {
    const failure = panel.linkFailure
    return (
      <>
        <Title />
        {failure.kind === 'rate-limited' ? (
          <RateLimitedAlert minutes={failure.minutes} />
        ) : (
          <FailureAlert failure={{ kind: failure.kind === 'network' ? 'network' : 'unexpected' }} />
        )}
      </>
    )
  }
  if (panel.account === undefined) {
    return <Title />
  }
  const minutes = panel.account.minutesLeft
  return (
    <>
      <Title>
        <p className="m-0 leading-[1.45] text-ink-3">
          {t('forAccount')}{' '}
          <span className="font-mono text-[14px] text-ink-2">{`@${panel.account.username}`}</span>
          {t('period')} {t('expiresIn')}{' '}
          {minutes === 1 ? t('oneMinute') : t('minutes', { count: minutes })}
          {t('period')}
        </p>
      </Title>
      {panel.alert?.kind === 'rate-limited' && <RateLimitedAlert minutes={panel.alert.minutes} />}
      {(panel.alert?.kind === 'network' || panel.alert?.kind === 'unexpected') && (
        <FailureAlert failure={panel.alert} />
      )}
      <form
        className="flex flex-col gap-3.5 sm:gap-5 lg:gap-4"
        noValidate
        onSubmit={(event) => {
          event.preventDefault()
          panel.submit()
        }}
      >
        <PasswordField
          label={t('newPassword')}
          autoComplete="new-password"
          maxLength={PASSWORD_INPUT_MAX}
          value={panel.password}
          onChange={panel.setPassword}
          describedBy={strengthId}
        />
        <div id={strengthId} className="flex flex-col gap-2">
          <StrengthMeter strength={panel.strength} />
          <PasswordRulesList rules={panel.rules} />
        </div>
        <PasswordField
          label={t('confirm')}
          autoComplete="new-password"
          maxLength={PASSWORD_INPUT_MAX}
          value={panel.confirm}
          onChange={panel.setConfirm}
          onBlur={panel.touchConfirm}
          error={panel.confirmMismatch ? t('mismatch') : null}
        />
        <div
          role="note"
          className="flex gap-2.5 rounded-[14px] bg-warning-soft px-3.5 py-3 text-[13px] leading-[1.4] text-ink-2 inset-ring inset-ring-warning-line"
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
            className="mt-px flex-none text-warning"
          >
            <path d="M14 4.5H6.5A1.5 1.5 0 0 0 5 6v12a1.5 1.5 0 0 0 1.5 1.5H14M10 12h10M16.5 8.5L20 12l-3.5 3.5" />
          </svg>
          <span>{t('sessionsNote')}</span>
        </div>
        <PrimaryButton disabled={!panel.canSubmit}>{t('submit')}</PrimaryButton>
      </form>
    </>
  )
}
