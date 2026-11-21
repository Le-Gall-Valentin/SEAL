import { useMessages } from '@/shared/i18n'
import { TextField } from '@/shared/ui'
import { accountMessages } from '../i18n'
import { DISPLAY_NAME_MAX_LENGTH } from '../model/displayName'

/** SEC-01 : bornes de la requête côté serveur, avant normalisation (ADR 0019). */
const DISPLAY_NAME_INPUT_MAX = DISPLAY_NAME_MAX_LENGTH * 4

interface DisplayNameFieldProps {
  readonly value: string
  readonly onChange: (value: string) => void
  readonly onBlur: () => void
  readonly invalid: boolean
}

/** AUTH-10, ACC-03 : « Nom affiché », d'Inscription-Telephone et d'Identifiant-OAuth. */
export function DisplayNameField({ value, onChange, onBlur, invalid }: DisplayNameFieldProps) {
  const t = useMessages(accountMessages)
  return (
    <TextField
      label={t('displayName')}
      autoComplete="name"
      maxLength={DISPLAY_NAME_INPUT_MAX}
      value={value}
      onChange={onChange}
      onBlur={onBlur}
      hint={t('displayNameHint')}
      error={invalid ? t('invalidDisplayName') : null}
    />
  )
}
