import { useId } from 'react'

interface SwitchProps {
  readonly label: string
  readonly checked: boolean
  readonly onChange: (checked: boolean) => void
  readonly disabled?: boolean
}

/**
 * Interrupteur de la maquette (Parametres-Telephone-Preferences) : bouton role="switch", 44 × 26 px dans une cible de
 * 52 × 44 px, libellé à gauche.
 */
export function Switch({ label, checked, onChange, disabled = false }: SwitchProps) {
  const labelId = useId()
  return (
    <div className="flex items-center gap-3">
      <span id={labelId} className="min-w-0 flex-1 text-[15px] font-semibold">
        {label}
      </span>
      <button
        type="button"
        role="switch"
        aria-checked={checked}
        aria-labelledby={labelId}
        disabled={disabled}
        className="grid h-11 w-13 flex-none place-items-center bg-transparent p-0 disabled:opacity-55"
        onClick={() => {
          onChange(!checked)
        }}
      >
        <span
          className={`flex h-6.5 w-11 rounded-full p-0.75 ${checked ? 'justify-end bg-accent' : 'justify-start bg-switch-off'}`}
        >
          <span
            className={`grid size-5 place-items-center rounded-full ${checked ? 'bg-on-accent' : 'bg-thumb-off'}`}
          >
            {checked && (
              <svg
                width="12"
                height="12"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="3"
                strokeLinecap="round"
                strokeLinejoin="round"
                aria-hidden="true"
                className="text-accent"
              >
                <path d="M5 12.5l4.5 4.5L19 7.5" />
              </svg>
            )}
          </span>
        </span>
      </button>
    </div>
  )
}
