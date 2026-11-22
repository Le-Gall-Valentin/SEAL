import type { RefObject } from 'react'
import { enterDigits } from '@/shared/lib'

interface CodeInputProps {
  /** Nom du groupe des six cases, annoncé par les lecteurs d'écran. */
  readonly label: string
  /** Nom de la case de rang index (1 à 6). */
  readonly boxLabel: (index: number) => string
  readonly cells: readonly string[]
  readonly onChange: (cells: readonly string[]) => void
  readonly disabled: boolean
  /** Cases, pour déplacer le focus ; la première reçoit le code proposé par le système. */
  readonly boxes: RefObject<(HTMLInputElement | null)[]>
}

/**
 * AUTH-14, ACC-02 : six cases d'un chiffre (Verification-Courriel, Dialogue-2FA ; ADR 0035). Le clavier numérique du système remplace celui que dessine
 * la maquette (inputmode="numeric") ; un code collé ou proposé par le système remplit les cases suivantes. ADR 0024,
 * point 11 : 48 px par case au plus, et toutes rétrécissent ensemble sur un téléphone de moins de 376 px.
 */
export function CodeInput({ label, boxLabel, cells, onChange, disabled, boxes }: CodeInputProps) {
  const focus = (index: number) => {
    boxes.current[index]?.focus()
  }
  return (
    <div role="group" aria-label={label} className="flex gap-2">
      {cells.map((cell, index) => (
        <input
          // Les six cases sont fixes et ordonnées : leur rang est leur identité.
          key={`case-${String(index)}`}
          ref={(element) => {
            boxes.current[index] = element
          }}
          aria-label={boxLabel(index + 1)}
          inputMode="numeric"
          autoComplete={index === 0 ? 'one-time-code' : 'off'}
          value={cell}
          disabled={disabled}
          className="h-14 max-w-12 min-w-0 flex-1 rounded-[10px] bg-surface text-center font-mono text-[22px] font-medium text-ink inset-ring inset-ring-line-strong outline-none focus:ring-3 focus:inset-ring-[1.5px] focus:ring-focus-glow focus:inset-ring-accent disabled:bg-sunken disabled:text-ink-3 nth-3:mr-2"
          onFocus={(event) => {
            event.target.select()
          }}
          onChange={(event) => {
            const input = event.target.value
            if (input === '') {
              onChange(cells.map((current, position) => (position === index ? '' : current)))
              return
            }
            const entered = enterDigits(cells, index, input)
            onChange(entered.cells)
            focus(entered.focus)
          }}
          onKeyDown={(event) => {
            if (event.key === 'Backspace' && cell === '' && index > 0) {
              event.preventDefault()
              onChange(cells.map((current, position) => (position === index - 1 ? '' : current)))
              focus(index - 1)
            }
          }}
        />
      ))}
    </div>
  )
}
