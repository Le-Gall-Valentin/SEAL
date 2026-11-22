import { NewPasswordField, PASSWORD_INPUT_MAX_LENGTH } from '@/entities/password'
import type { Account } from '@/entities/session'
import { useMessages } from '@/shared/i18n'
import {
  DialogBody,
  DialogFooter,
  FormAlert,
  ModalDialog,
  PasswordField,
  Switch,
} from '@/shared/ui'
import { passwordChangeMessages } from '../i18n'
import { usePasswordChange } from '../model/usePasswordChange'

interface ChangePasswordDialogProps {
  readonly open: boolean
  readonly account: Account
  readonly onChanged: () => void
  readonly onCancel: () => void
}

/** ACC-01 : « Changer le mot de passe », dialogue validé par Valentin (ADR 0035, point 1). */
export function ChangePasswordDialog({
  open,
  account,
  onChanged,
  onCancel,
}: ChangePasswordDialogProps) {
  const t = useMessages(passwordChangeMessages)
  const form = usePasswordChange(account, onChanged)
  const cancel = () => {
    form.clear()
    onCancel()
  }
  return (
    <ModalDialog open={open} onClose={cancel} closable={!form.pending} title={t('dialogTitle')}>
      <form
        noValidate
        onSubmit={(event) => {
          event.preventDefault()
          form.submit()
        }}
      >
        <DialogBody>
          <FormAlert failure={form.formFailure} />
          <PasswordField
            label={t('current')}
            autoComplete="current-password"
            maxLength={PASSWORD_INPUT_MAX_LENGTH}
            value={form.current}
            onChange={form.setCurrent}
            disabled={form.locked}
            error={form.currentRefused ? t('invalidCurrent') : null}
          />
          <NewPasswordField
            label={t('next')}
            value={form.next}
            onChange={form.setNext}
            rules={form.rules}
            strength={form.strength}
            disabled={form.locked}
          />
          <Switch
            label={t('signOut')}
            checked={form.signOut}
            onChange={form.setSignOut}
            disabled={form.locked}
          />
        </DialogBody>
        <DialogFooter
          onCancel={cancel}
          cancelable={!form.pending}
          submitLabel={t('submit')}
          canSubmit={form.canSubmit}
        />
      </form>
    </ModalDialog>
  )
}
