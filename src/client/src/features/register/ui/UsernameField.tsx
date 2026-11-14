import { useId } from 'react'
import { useMessages } from '@/shared/i18n'
import { ErrorText, FieldLabel, Sceau } from '@/shared/ui'
import { registerMessages } from '../i18n'
import type { UsernameStatus } from '../model/useUsernameAvailability'

interface UsernameFieldProps {
  readonly value: string
  readonly onChange: (value: string) => void
  readonly status: UsernameStatus
  /** AUTH-04 : l'identifiant suit encore la suggestion tirée du courriel. */
  readonly fromEmail: boolean
  readonly maxLength: number
}

function StatusIcon({ status }: { readonly status: UsernameStatus }) {
  switch (status.kind) {
    case 'available':
      return <Sceau variant="verified" size={18} decorative />
    case 'taken':
    case 'invalid':
    case 'reserved':
      return (
        <svg
          width="18"
          height="18"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="1.9"
          strokeLinecap="round"
          aria-hidden="true"
          className="flex-none text-danger"
        >
          <path d="M6.5 6.5l11 11M17.5 6.5l-11 11" />
        </svg>
      )
    case 'empty':
    case 'checking':
    case 'unknown':
      return null
  }
}

function StatusText({ status, fromEmail }: Pick<UsernameFieldProps, 'status' | 'fromEmail'>) {
  const t = useMessages(registerMessages)
  switch (status.kind) {
    case 'available':
      return (
        <>
          <span className="font-semibold text-accent-text">{t('available')}</span>
          {fromEmail && <span>{t('fromEmail')}</span>}
        </>
      )
    case 'taken':
      return <ErrorText>{t('taken')}</ErrorText>
    case 'invalid':
      return <ErrorText>{t('invalidUsername')}</ErrorText>
    case 'reserved':
      return <ErrorText>{t('reserved')}</ErrorText>
    case 'empty':
    case 'checking':
    case 'unknown':
      return null
  }
}

/** AUTH-04, AUTH-05 : identifiant précédé de « @ », disponibilité en direct et suggestions (Identifiant-OAuth). */
export function UsernameField({
  value,
  onChange,
  status,
  fromEmail,
  maxLength,
}: UsernameFieldProps) {
  const t = useMessages(registerMessages)
  const id = useId()
  const hintId = `${id}-etat`
  const refused = status.kind === 'taken' || status.kind === 'invalid' || status.kind === 'reserved'
  return (
    <div className="flex flex-col gap-1.5">
      <FieldLabel htmlFor={id}>{t('username')}</FieldLabel>
      <div className="flex h-11 items-center gap-1.5 rounded-[10px] bg-surface px-3 inset-ring inset-ring-line-strong focus-within:ring-3 focus-within:ring-focus-glow focus-within:inset-ring-accent">
        <span aria-hidden="true" className="font-mono text-[15px] text-ink-3">
          @
        </span>
        <input
          id={id}
          value={value}
          autoComplete="username"
          maxLength={maxLength}
          spellCheck={false}
          autoCapitalize="none"
          aria-invalid={refused ? true : undefined}
          aria-describedby={hintId}
          className="min-w-0 flex-1 border-0 bg-transparent font-mono text-[15px] tracking-[0.01em] text-ink outline-none"
          onChange={(event) => {
            onChange(event.target.value)
          }}
        />
        <StatusIcon status={status} />
      </div>
      <div id={hintId}>
        <div
          aria-live="polite"
          className="flex min-h-5 flex-wrap items-center gap-1.5 text-[13px] leading-[1.35] text-ink-3"
        >
          <StatusText status={status} fromEmail={fromEmail} />
        </div>
        {(status.kind === 'taken' || status.kind === 'reserved') &&
          status.suggestions.length > 0 && (
            <div className="mt-1.5 flex flex-wrap items-center gap-1.5 text-[13px] text-ink-3">
              <span>{t('suggestions')}</span>
              {status.suggestions.map((suggestion) => (
                <button
                  key={suggestion}
                  type="button"
                  className="min-h-11 rounded-full bg-accent-soft px-3 font-mono text-[13px] text-accent-text"
                  onClick={() => {
                    onChange(suggestion)
                  }}
                >
                  {suggestion}
                </button>
              ))}
            </div>
          )}
      </div>
    </div>
  )
}
