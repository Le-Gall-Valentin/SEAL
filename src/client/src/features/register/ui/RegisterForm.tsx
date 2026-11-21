import {
  DisplayNameField,
  EMAIL_MAX_LENGTH,
  FinalUsernameNotice,
  USERNAME_MAX_LENGTH,
  UsernameField,
} from '@/entities/account'
import { CompositionNote, NewPasswordFields } from '@/entities/password'
import { useMessages } from '@/shared/i18n'
import { FormAlert, PrimaryButton, TextField } from '@/shared/ui'
import { registerMessages } from '../i18n'
import { useRegisterForm } from '../model/useRegisterForm'

/**
 * AUTH-03 à AUTH-05, AUTH-10, AUTH-11 : formulaire d'Inscription-Telephone ; les boutons des fournisseurs sont rendus
 * par l'écran (ADR 0033).
 */
export function RegisterForm() {
  const t = useMessages(registerMessages)
  const form = useRegisterForm()
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
        <DisplayNameField
          value={form.values.displayName}
          onChange={form.setDisplayName}
          onBlur={form.touchDisplayName}
          invalid={form.displayNameInvalid}
        />
        <NewPasswordFields
          passwordLabel={t('password')}
          password={form.values.password}
          confirm={form.values.confirm}
          onPasswordChange={form.setPassword}
          onConfirmChange={form.setConfirm}
          onConfirmBlur={form.touchConfirm}
          confirmMismatch={form.confirmMismatch}
          rules={form.rules}
          strength={form.strength}
        >
          <CompositionNote />
        </NewPasswordFields>
        <PrimaryButton disabled={!form.canSubmit}>{t('submit')}</PrimaryButton>
      </form>
    </>
  )
}
