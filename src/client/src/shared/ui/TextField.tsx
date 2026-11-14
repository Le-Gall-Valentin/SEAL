import { type Ref, useId } from 'react'
import { FieldError, FieldLabel } from './Field'

const INPUT_CLASS =
  'h-11 w-full rounded-[10px] bg-surface px-3 font-sans text-[15px] text-ink inset-ring inset-ring-line-strong outline-none focus:inset-ring-accent focus:ring-3 focus:ring-focus-glow disabled:bg-sunken disabled:text-ink-3'

/** Aide sous un champ (« Visible par vos amis… »). */
const HINT_CLASS = 'text-[13px] leading-[1.35] text-ink-3'

interface TextFieldProps {
  readonly label: string
  readonly value: string
  readonly onChange: (value: string) => void
  readonly autoComplete: string
  /** SEC-01 : longueur maximale, celle du serveur. */
  readonly maxLength: number
  readonly type?: 'text' | 'email'
  readonly disabled?: boolean
  readonly hint?: string | undefined
  /** Erreur affichée sous le champ et annoncée ; null ou absente si le champ est valide. */
  readonly error?: string | null | undefined
  readonly onBlur?: (() => void) | undefined
  /** Pour placer le focus sur le champ (ouverture d'un formulaire sur place). */
  readonly inputRef?: Ref<HTMLInputElement> | undefined
}

/** Champ étiqueté des formulaires de la maquette, avec son aide et son erreur. */
export function TextField({
  label,
  value,
  onChange,
  autoComplete,
  maxLength,
  type = 'text',
  disabled = false,
  hint,
  error,
  onBlur,
  inputRef,
}: TextFieldProps) {
  const id = useId()
  const hintId = `${id}-aide`
  const errorId = `${id}-erreur`
  const hasError = error !== undefined && error !== null
  const describedBy = [hint !== undefined ? hintId : null, hasError ? errorId : null]
    .filter((part) => part !== null)
    .join(' ')
  return (
    <div className="flex flex-col gap-1.5">
      <FieldLabel htmlFor={id}>{label}</FieldLabel>
      <input
        ref={inputRef}
        id={id}
        type={type}
        value={value}
        autoComplete={autoComplete}
        maxLength={maxLength}
        disabled={disabled}
        spellCheck={false}
        autoCapitalize="none"
        aria-invalid={hasError ? true : undefined}
        aria-describedby={describedBy === '' ? undefined : describedBy}
        className={INPUT_CLASS}
        onChange={(event) => {
          onChange(event.target.value)
        }}
        onBlur={onBlur}
      />
      {hint !== undefined && (
        <span id={hintId} className={HINT_CLASS}>
          {hint}
        </span>
      )}
      <FieldError id={errorId} error={hasError ? error : null} />
    </div>
  )
}
