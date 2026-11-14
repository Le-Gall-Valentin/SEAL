import { type ReactNode, useId } from 'react'
import { useMessages } from '@/shared/i18n'
import { PasswordField } from '@/shared/ui'
import { passwordMessages } from '../i18n'
import { PASSWORD_INPUT_MAX_LENGTH } from '../model/passwordForm'
import type { PasswordRules } from '../model/passwordPolicy'
import type { PasswordStrength } from '../model/passwordStrength'
import { PasswordRulesList } from './PasswordRulesList'
import { StrengthMeter } from './StrengthMeter'

interface NewPasswordFieldsProps {
  /** « Mot de passe » (Inscription-Telephone) ou « Nouveau mot de passe » (Nouveau-Mot-de-passe). */
  readonly passwordLabel: string
  readonly password: string
  readonly confirm: string
  readonly onPasswordChange: (value: string) => void
  readonly onConfirmChange: (value: string) => void
  readonly onConfirmBlur: () => void
  readonly confirmMismatch: boolean
  readonly rules: PasswordRules
  readonly strength: PasswordStrength
  /** Lignes ajoutées après les règles, propres à l'écran. */
  readonly children?: ReactNode
}

/**
 * AUTH-11 : nouveau mot de passe, jauge « Robustesse », règles en direct et confirmation, communs à
 * Inscription-Telephone et Nouveau-Mot-de-passe. Le champ est décrit par la jauge et les règles.
 */
export function NewPasswordFields({
  passwordLabel,
  password,
  confirm,
  onPasswordChange,
  onConfirmChange,
  onConfirmBlur,
  confirmMismatch,
  rules,
  strength,
  children,
}: NewPasswordFieldsProps) {
  const t = useMessages(passwordMessages)
  const strengthId = useId()
  return (
    <>
      <PasswordField
        label={passwordLabel}
        autoComplete="new-password"
        maxLength={PASSWORD_INPUT_MAX_LENGTH}
        value={password}
        onChange={onPasswordChange}
        describedBy={strengthId}
      />
      <div id={strengthId} className="flex flex-col gap-2">
        <StrengthMeter strength={strength} />
        <PasswordRulesList rules={rules}>{children}</PasswordRulesList>
      </div>
      <PasswordField
        label={t('confirm')}
        autoComplete="new-password"
        maxLength={PASSWORD_INPUT_MAX_LENGTH}
        value={confirm}
        onChange={onConfirmChange}
        onBlur={onConfirmBlur}
        error={confirmMismatch ? t('mismatch') : null}
      />
    </>
  )
}
