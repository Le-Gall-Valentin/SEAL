import { useEffect, useId, useRef } from 'react'
import { EMAIL_MAX_LENGTH } from '@/entities/account'
import type { Account } from '@/entities/session'
import { useMessages } from '@/shared/i18n'
import {
  Alert,
  CodeInput,
  DialogBody,
  DialogFooter,
  FormAlert,
  ModalDialog,
  TextField,
} from '@/shared/ui'
import { emailChangeMessages } from '../i18n'
import { useEmailChange } from '../model/useEmailChange'

interface ChangeEmailDialogProps {
  readonly open: boolean
  readonly account: Account
  readonly onReauthRequired: (retry: () => void) => void
  readonly onChanged: () => void
  readonly onCancel: () => void
}

/** ACC-02 : « Changer de courriel », en deux étapes ; dialogue validé par Valentin (ADR 0035, point 1). */
export function ChangeEmailDialog({
  open,
  account,
  onReauthRequired,
  onChanged,
  onCancel,
}: ChangeEmailDialogProps) {
  const t = useMessages(emailChangeMessages)
  const introId = useId()
  const boxes = useRef<(HTMLInputElement | null)[]>([])
  const change = useEmailChange({ account, onReauthRequired, onChanged })
  // Étape 2, ou cases vidées après un code refusé : la saisie reprend à la case 1, comme Verification-Courriel. La
  // demande attend la fin de l'envoi : une case désactivée ne prend pas le focus.
  const focusHandled = useRef(0)
  useEffect(() => {
    if (change.codeFocus > focusHandled.current && !change.pending) {
      focusHandled.current = change.codeFocus
      boxes.current[0]?.focus()
    }
  }, [change.codeFocus, change.pending])
  const cancel = () => {
    change.clear()
    onCancel()
  }
  const addressStep = change.step === 'address'
  return (
    <ModalDialog
      open={open}
      onClose={cancel}
      closable={!change.pending}
      eyebrow={addressStep ? t('step1') : t('step2')}
      title={addressStep ? t('addressTitle') : t('codeTitle')}
      describedBy={introId}
    >
      {addressStep ? (
        <form
          noValidate
          onSubmit={(event) => {
            event.preventDefault()
            change.sendAddress()
          }}
        >
          <DialogBody>
            <p id={introId} className="m-0 text-[14.5px] leading-[1.45] text-ink-2">
              {t('addressIntro')}
            </p>
            <FormAlert failure={change.sendFailure} />
            <TextField
              label={t('newEmail')}
              type="email"
              autoComplete="email"
              maxLength={EMAIL_MAX_LENGTH}
              value={change.email}
              onChange={change.setEmail}
              onBlur={change.touchEmail}
              disabled={change.sendLocked}
              error={change.emailInvalid ? t('invalidEmail') : null}
            />
          </DialogBody>
          <DialogFooter
            onCancel={cancel}
            cancelable={!change.pending}
            submitLabel={t('sendCode')}
            canSubmit={change.canSend}
          />
        </form>
      ) : (
        <form
          noValidate
          onSubmit={(event) => {
            event.preventDefault()
            change.confirmCode()
          }}
        >
          <DialogBody>
            <p id={introId} className="m-0 text-[14.5px] leading-[1.45] wrap-anywhere text-ink-2">
              {t('codeIntro', { email: change.target })}
            </p>
            <FormAlert failure={change.confirmFailure ?? change.sendFailure} />
            <p role="status" className="m-0 text-[13px] wrap-anywhere text-ink-2 empty:hidden">
              {change.resentTo !== null && t('sent', { email: change.resentTo })}
            </p>
            {change.codeFailure !== null && (
              <Alert variant="error">
                {change.codeFailure === 'expired' ? t('expired') : t('invalidCode')}
              </Alert>
            )}
            <CodeInput
              label={t('code')}
              boxLabel={(index) => t('codeBox', { index })}
              cells={change.cells}
              onChange={change.setCells}
              disabled={change.pending}
              boxes={boxes}
            />
            <button
              type="button"
              disabled={!change.canResend}
              className="-ml-3 inline-flex min-h-11 items-center gap-2 self-start rounded-[10px] px-3 font-sans text-[14px] font-semibold whitespace-nowrap text-accent-text disabled:opacity-55"
              onClick={change.resend}
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
          </DialogBody>
          <DialogFooter
            onCancel={cancel}
            cancelable={!change.pending}
            submitLabel={t('confirm')}
            canSubmit={change.canConfirm}
          />
        </form>
      )}
    </ModalDialog>
  )
}
