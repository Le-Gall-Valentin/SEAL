import { useId } from 'react'

export const INPUT_CLASS =
  'h-11 w-full rounded-[10px] bg-surface px-3 font-sans text-[15px] text-ink inset-ring inset-ring-line-strong outline-none focus:inset-ring-accent focus:ring-3 focus:ring-focus-glow disabled:bg-sunken disabled:text-ink-3'

export const LABEL_CLASS = 'text-[13px] font-semibold text-ink-2'

interface TextFieldProps {
  readonly label: string
  readonly value: string
  readonly onChange: (value: string) => void
  readonly autoComplete: string
  /** SEC-01 : longueur maximale, celle du serveur. */
  readonly maxLength: number
  readonly type?: 'text' | 'email'
  readonly disabled?: boolean
}

/** Champ étiqueté des formulaires de la maquette. */
export function TextField({
  label,
  value,
  onChange,
  autoComplete,
  maxLength,
  type = 'text',
  disabled = false,
}: TextFieldProps) {
  const id = useId()
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className={LABEL_CLASS}>
        {label}
      </label>
      <input
        id={id}
        type={type}
        value={value}
        autoComplete={autoComplete}
        maxLength={maxLength}
        disabled={disabled}
        spellCheck={false}
        autoCapitalize="none"
        className={INPUT_CLASS}
        onChange={(event) => {
          onChange(event.target.value)
        }}
      />
    </div>
  )
}
