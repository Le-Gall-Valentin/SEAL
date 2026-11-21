import {
  DisplayNameField,
  FinalUsernameNotice,
  USERNAME_MAX_LENGTH,
  UsernameField,
} from '@/entities/account'
import { useMessages } from '@/shared/i18n'
import { FormAlert, PrimaryButton } from '@/shared/ui'
import { oauthRegistrationMessages } from '../i18n'
import type { PendingRegistration } from '../model/oauthRegistration'
import { useOAuthRegistrationForm } from '../model/useOAuthRegistrationForm'

/** AUTH-03 à AUTH-05, AUTH-32 : formulaire d'Identifiant-OAuth, identifiant et nom affiché. */
export function OAuthRegistrationForm({ pending }: { readonly pending: PendingRegistration }) {
  const t = useMessages(oauthRegistrationMessages)
  const form = useOAuthRegistrationForm(pending)
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
        <div className="flex flex-col gap-2.5">
          <UsernameField
            value={form.username}
            onChange={form.setUsername}
            status={form.usernameStatus}
            fromEmail={form.usernameFromEmail}
            maxLength={USERNAME_MAX_LENGTH}
          />
          <FinalUsernameNotice />
        </div>
        <DisplayNameField
          value={form.displayName}
          onChange={form.setDisplayName}
          onBlur={form.touchDisplayName}
          invalid={form.displayNameInvalid}
        />
        <PrimaryButton disabled={!form.canSubmit}>{t('submit')}</PrimaryButton>
      </form>
    </>
  )
}
