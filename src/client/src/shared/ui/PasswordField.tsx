import { useId, useState } from 'react'
import { useMessages } from '@/shared/i18n'
import { formMessages } from './formMessages'
import { FieldError, FieldLabel } from './Field'

interface PasswordFieldProps {
  readonly label: string
  readonly value: string
  readonly onChange: (value: string) => void
  readonly autoComplete: 'current-password' | 'new-password'
  /** SEC-01 : longueur maximale, celle du serveur. */
  readonly maxLength: number
  readonly disabled?: boolean
  /** Élément qui décrit le champ (jauge et règles du mot de passe). */
  readonly describedBy?: string | undefined
  /** Erreur affichée sous le champ et annoncée ; null ou absente si le champ est valide. */
  readonly error?: string | null | undefined
  readonly onBlur?: (() => void) | undefined
}

/** Mot de passe masqué, avec « Afficher le mot de passe » (bouton à bascule, aria-pressed). */
export function PasswordField({
  label,
  value,
  onChange,
  autoComplete,
  maxLength,
  disabled = false,
  describedBy,
  error,
  onBlur,
}: PasswordFieldProps) {
  const id = useId()
  const errorId = `${id}-erreur`
  const hasError = error !== undefined && error !== null
  const description = [describedBy ?? null, hasError ? errorId : null]
    .filter((part) => part !== null)
    .join(' ')
  const t = useMessages(formMessages)
  const [visible, setVisible] = useState(false)
  return (
    <div className="flex flex-col gap-1.5">
      <FieldLabel htmlFor={id}>{label}</FieldLabel>
      <div className="flex h-11 items-center rounded-[10px] bg-surface pl-3 inset-ring inset-ring-line-strong focus-within:ring-3 focus-within:ring-focus-glow focus-within:inset-ring-accent has-disabled:bg-sunken sm:pr-1">
        <input
          id={id}
          type={visible ? 'text' : 'password'}
          value={value}
          autoComplete={autoComplete}
          maxLength={maxLength}
          disabled={disabled}
          spellCheck={false}
          autoCapitalize="none"
          aria-invalid={hasError ? true : undefined}
          aria-describedby={description === '' ? undefined : description}
          onBlur={onBlur}
          className="min-w-0 flex-1 border-0 bg-transparent font-sans text-[15px] text-ink outline-none disabled:text-ink-3"
          onChange={(event) => {
            onChange(event.target.value)
          }}
        />
        <button
          type="button"
          aria-label={t('showPassword')}
          aria-pressed={visible}
          disabled={disabled}
          className="grid size-11 flex-none place-items-center rounded-[10px] text-ink-2 disabled:text-ink-3 disabled:opacity-55 sm:size-9 sm:rounded-lg"
          onClick={() => {
            setVisible((shown) => !shown)
          }}
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
            <path d="M2.5 12s3.5-6.5 9.5-6.5 9.5 6.5 9.5 6.5-3.5 6.5-9.5 6.5S2.5 12 2.5 12z" />
            <circle cx="12" cy="12" r="2.8" />
          </svg>
        </button>
      </div>
      <FieldError id={errorId} error={hasError ? error : null} />
    </div>
  )
}
