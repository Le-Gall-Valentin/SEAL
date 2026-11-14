import { useId } from 'react'
import { DISPLAY_NAME_MAX_LENGTH, EMAIL_MAX_LENGTH, USERNAME_MAX_LENGTH } from '@/entities/account'
import { CompositionNote, PasswordRulesList, StrengthMeter } from '@/entities/password'
import { useMessages } from '@/shared/i18n'
import {
  FailureAlert,
  PasswordField,
  PrimaryButton,
  RateLimitedAlert,
  TextField,
} from '@/shared/ui'
import { registerMessages } from '../i18n'
import { useRegisterForm } from '../model/useRegisterForm'
import { FinalUsernameNotice } from './FinalUsernameNotice'
import { UsernameField } from './UsernameField'

/** SEC-01 : bornes de RegisterRequest côté serveur, avant normalisation (ADR 0019). */
const DISPLAY_NAME_INPUT_MAX = DISPLAY_NAME_MAX_LENGTH * 4
const PASSWORD_INPUT_MAX = 512

/** AUTH-03 à AUTH-05, AUTH-10, AUTH-11 : formulaire d'Inscription-Telephone, sans OAuth (ADR 0024, point 3). */
export function RegisterForm() {
  const t = useMessages(registerMessages)
  const form = useRegisterForm()
  const strengthId = useId()
  return (
    <>
      {form.alert?.kind === 'rate-limited' && <RateLimitedAlert minutes={form.alert.minutes} />}
      {(form.alert?.kind === 'network' || form.alert?.kind === 'unexpected') && (
        <FailureAlert failure={form.alert} />
      )}
      <form
        className="flex flex-col gap-3.5 sm:gap-5 lg:gap-4"
        noValidate
        onSubmit={(event) => {
          event.preventDefault()
          form.submit()
        }}
      >
        <TextField
          label={t('email')}
          type="email"
          autoComplete="email"
          maxLength={EMAIL_MAX_LENGTH}
          value={form.values.email}
          onChange={form.setEmail}
          onBlur={form.touchEmail}
          error={form.emailInvalid ? t('invalidEmail') : null}
        />
        <div className="flex flex-col gap-2.5">
          <UsernameField
            value={form.values.username}
            onChange={form.setUsername}
            status={form.usernameStatus}
            fromEmail={form.usernameFromEmail}
            maxLength={USERNAME_MAX_LENGTH}
          />
          <FinalUsernameNotice />
        </div>
        <TextField
          label={t('displayName')}
          autoComplete="name"
          maxLength={DISPLAY_NAME_INPUT_MAX}
          value={form.values.displayName}
          onChange={form.setDisplayName}
          onBlur={form.touchDisplayName}
          hint={t('displayNameHint')}
          error={form.displayNameInvalid ? t('invalidDisplayName') : null}
        />
        <PasswordField
          label={t('password')}
          autoComplete="new-password"
          maxLength={PASSWORD_INPUT_MAX}
          value={form.values.password}
          onChange={form.setPassword}
          describedBy={strengthId}
        />
        <div id={strengthId} className="flex flex-col gap-2">
          <StrengthMeter strength={form.strength} />
          <PasswordRulesList rules={form.rules}>
            <CompositionNote />
          </PasswordRulesList>
        </div>
        <PasswordField
          label={t('confirm')}
          autoComplete="new-password"
          maxLength={PASSWORD_INPUT_MAX}
          value={form.values.confirm}
          onChange={form.setConfirm}
          onBlur={form.touchConfirm}
          error={form.confirmMismatch ? t('mismatch') : null}
        />
        <PrimaryButton disabled={!form.canSubmit}>{t('submit')}</PrimaryButton>
      </form>
    </>
  )
}
