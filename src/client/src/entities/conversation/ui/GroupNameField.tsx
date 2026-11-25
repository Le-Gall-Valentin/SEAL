import type { Ref } from 'react'
import { useMessages } from '@/shared/i18n'
import { TextField } from '@/shared/ui'
import { GROUP_NAME_RAW_MAX_LENGTH } from '../model/conversations'
import { conversationMessages } from '../i18n'

interface GroupNameFieldProps {
  readonly value: string
  readonly onChange: (value: string) => void
  readonly onBlur: () => void
  readonly invalid: boolean
  readonly disabled?: boolean
  readonly inputRef?: Ref<HTMLInputElement> | undefined
}

/**
 * GRP-01 : « Nom du groupe » de Nouvelle-Conversation, sans placeholder, repris pour le renommage (ADR 0041, point
 * 3). 128 unités UTF-16 au plus, la borne brute du serveur (SEC-01) ; la règle de 64 caractères se vérifie en NFC.
 */
export function GroupNameField({
  value,
  onChange,
  onBlur,
  invalid,
  disabled = false,
  inputRef,
}: GroupNameFieldProps) {
  const t = useMessages(conversationMessages)
  return (
    <TextField
      label={t('groupName')}
      autoComplete="off"
      maxLength={GROUP_NAME_RAW_MAX_LENGTH}
      value={value}
      onChange={onChange}
      onBlur={onBlur}
      disabled={disabled}
      error={invalid ? t('invalidGroupName') : null}
      inputRef={inputRef}
    />
  )
}
