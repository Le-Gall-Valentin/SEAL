import { EMAIL_MAX_LENGTH } from '@/entities/account'
import { useMessages } from '@/shared/i18n'
import { FormAlert, Notice, PrimaryButton, TextField } from '@/shared/ui'
import { passwordResetMessages } from '../i18n'
import { useForgotPassword } from '../model/useForgotPassword'

/** AUTH-70 : formulaire de Mot-de-passe-oublie, confirmation et rappel des sessions fermées. */
export function ForgotPasswordForm() {
  const t = useMessages(passwordResetMessages)
  const form = useForgotPassword()
  return (
    <>
      <FormAlert failure={form.alert} />
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
          value={form.email}
          onChange={form.setEmail}
          onBlur={form.touch}
          error={form.emailInvalid ? t('invalidEmail') : null}
        />
        <PrimaryButton disabled={!form.canSubmit}>{t('send')}</PrimaryButton>
      </form>
      <div role="status" className="empty:hidden">
        {form.sent && (
          <Notice variant="mail" title={t('sentTitle')}>
            {t('sentBody')}
          </Notice>
        )}
      </div>
      <div className="flex gap-3 rounded-[14px] bg-sunken px-3.5 py-3 text-[13px] leading-[1.4] text-ink-2 inset-ring inset-ring-line">
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
          className="mt-px flex-none"
        >
          <circle cx="8" cy="15" r="4" />
          <path d="M11 12l8.5-8.5M16.5 6.5L19 9M14 9l2 2" />
        </svg>
        <span>{t('sessionsInfo')}</span>
      </div>
    </>
  )
}
