import { Link } from 'react-router'
import { PASSWORD_INPUT_MAX_LENGTH } from '@/entities/password'
import { ROUTES } from '@/shared/config'
import { useMessages } from '@/shared/i18n'
import {
  Alert,
  FailureAlert,
  PasswordField,
  PrimaryButton,
  RateLimitedAlert,
  TextField,
} from '@/shared/ui'
import { loginMessages } from '../i18n'
import type { LoginAlert } from '../model/loginAlert'
import { useLoginForm } from '../model/useLoginForm'

function LoginAlertView({ alert }: { readonly alert: LoginAlert }) {
  const t = useMessages(loginMessages)
  switch (alert.kind) {
    case 'invalid-credentials':
      return <Alert variant="error">{t('invalidCredentials')}</Alert>
    case 'rate-limited':
      return <RateLimitedAlert minutes={alert.minutes} hint={t('resetHint')} />
    case 'network':
    case 'unexpected':
      return <FailureAlert failure={alert} />
  }
}

/** AUTH-20 à AUTH-22 : formulaire de connexion, sans OAuth au lot 1 (ADR 0024, point 3). */
export function LoginForm() {
  const t = useMessages(loginMessages)
  const form = useLoginForm()
  return (
    <>
      {form.alert !== null && <LoginAlertView alert={form.alert} />}
      <form
        className="flex flex-col gap-3.5 sm:gap-5 lg:gap-4"
        noValidate
        onSubmit={(event) => {
          event.preventDefault()
          form.submit()
        }}
      >
        <TextField
          label={t('login')}
          autoComplete="username"
          maxLength={254}
          value={form.values.login}
          onChange={form.setLogin}
          disabled={form.locked}
        />
        <PasswordField
          label={t('password')}
          autoComplete="current-password"
          maxLength={PASSWORD_INPUT_MAX_LENGTH}
          value={form.values.password}
          onChange={form.setPassword}
          disabled={form.locked}
        />
        <Link
          to={ROUTES.FORGOT_PASSWORD}
          className="-mt-3 -mb-2 inline-flex min-h-11 items-center self-end text-[13px] font-semibold no-underline sm:mt-[-8px] sm:mb-0 sm:min-h-8"
        >
          {t('forgotPassword')}
        </Link>
        <PrimaryButton disabled={!form.canSubmit}>{t('submit')}</PrimaryButton>
      </form>
    </>
  )
}
