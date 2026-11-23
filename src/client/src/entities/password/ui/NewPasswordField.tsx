import { type ReactNode, useId } from 'react'
import { PasswordField } from '@/shared/ui'
import { PASSWORD_INPUT_MAX_LENGTH } from '../model/passwordForm'
import type { PasswordRules } from '../model/passwordPolicy'
import type { PasswordStrength } from '../model/passwordStrength'
import { PasswordRulesList } from './PasswordRulesList'
import { StrengthMeter } from './StrengthMeter'

interface NewPasswordFieldProps {
  readonly label: string
  readonly value: string
  readonly onChange: (value: string) => void
  readonly rules: PasswordRules
  readonly strength: PasswordStrength
  readonly disabled?: boolean
  /** Lignes ajoutées après les règles, propres à l'écran. */
  readonly children?: ReactNode
}

/**
 * AUTH-11 : nouveau mot de passe, décrit par la jauge « Robustesse » et les règles en direct. Seul dans le changement
 * de mot de passe (ACC-01, ADR 0035) ; suivi de la confirmation à l'inscription et à la réinitialisation.
 */
export function NewPasswordField({
  label,
  value,
  onChange,
  rules,
  strength,
  disabled = false,
  children,
}: NewPasswordFieldProps) {
  const strengthId = useId()
  return (
    <>
      <PasswordField
        label={label}
        autoComplete="new-password"
        maxLength={PASSWORD_INPUT_MAX_LENGTH}
        value={value}
        onChange={onChange}
        describedBy={strengthId}
        disabled={disabled}
      />
      <div id={strengthId} className="flex flex-col gap-2">
        <StrengthMeter strength={strength} />
        <PasswordRulesList rules={rules}>{children}</PasswordRulesList>
      </div>
    </>
  )
}
