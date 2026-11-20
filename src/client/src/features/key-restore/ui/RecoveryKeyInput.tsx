import { useId, useRef } from 'react'
import { useMessages } from '@/shared/i18n'
import { keyRestoreMessages } from '../i18n'

interface RecoveryKeyInputProps {
  readonly groups: readonly string[]
  /** Saisie dans une case ; rend la case qui doit prendre le focus. */
  readonly onEnter: (index: number, input: string) => number
  readonly disabled: boolean
}

/**
 * KEY-33 : la clé de récupération en 8 cases de 4 caractères (Restauration-Appareil). Une clé collée d'un bloc se
 * répartit ; les majuscules ne sont que d'affichage. Sans maxlength : le navigateur tronquerait un collage.
 */
export function RecoveryKeyInput({ groups, onEnter, disabled }: RecoveryKeyInputProps) {
  const t = useMessages(keyRestoreMessages)
  const labelId = useId()
  const hintId = useId()
  const boxes = useRef<(HTMLInputElement | null)[]>([])
  return (
    <div className="flex flex-col gap-2">
      <span id={labelId} className="text-[13px] font-semibold text-ink-2">
        {t('label')}
      </span>
      <div
        role="group"
        aria-labelledby={labelId}
        aria-describedby={hintId}
        className="grid grid-cols-4 gap-2"
      >
        {groups.map((group, index) => (
          <input
            // Les huit cases sont fixes et ordonnées : leur rang est leur identité.
            key={`groupe-${String(index)}`}
            ref={(element) => {
              boxes.current[index] = element
            }}
            aria-label={t('groupBox', { index: index + 1 })}
            value={group}
            disabled={disabled}
            autoComplete="off"
            spellCheck={false}
            autoCapitalize="characters"
            className="h-11 w-full min-w-0 rounded-[10px] bg-surface text-center font-mono text-[15px] font-medium tracking-[0.08em] text-ink uppercase inset-ring inset-ring-line-strong outline-none focus:ring-3 focus:inset-ring-[1.5px] focus:ring-focus-glow focus:inset-ring-accent disabled:bg-sunken disabled:text-ink-3"
            onChange={(event) => {
              boxes.current[onEnter(index, event.target.value)]?.focus()
            }}
            onKeyDown={(event) => {
              if (event.key === 'Backspace' && group === '' && index > 0) {
                event.preventDefault()
                boxes.current[index - 1]?.focus()
              }
            }}
          />
        ))}
      </div>
      <span id={hintId} className="text-[13px] leading-[1.4] text-ink-3">
        {t('hint')}
      </span>
    </div>
  )
}
