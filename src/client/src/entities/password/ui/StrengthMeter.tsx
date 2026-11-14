import { useMessages } from '@/shared/i18n'
import { passwordMessages } from '../i18n'
import type { PasswordStrength } from '../model/passwordStrength'

const LEVELS = ['level0', 'level1', 'level2', 'level3', 'level4'] as const
const SEGMENTS = [1, 2, 3, 4] as const

/** Jauge « Robustesse » de la maquette, quatre segments (ADR 0007, point 6). */
export function StrengthMeter({ strength }: { readonly strength: PasswordStrength }) {
  const t = useMessages(passwordMessages)
  const label = t(LEVELS[strength])
  return (
    <div className="flex items-center gap-3">
      <div
        role="meter"
        aria-label={t('strength')}
        aria-valuemin={0}
        aria-valuemax={4}
        aria-valuenow={strength}
        aria-valuetext={label}
        className="grid flex-1 grid-cols-4 gap-1"
      >
        {SEGMENTS.map((segment) => (
          <span
            key={segment}
            data-filled={segment <= strength}
            className="h-1 rounded-[2px] bg-line data-[filled=true]:bg-accent"
          />
        ))}
      </div>
      <span className="min-w-20 text-right text-[13px] font-semibold text-ink-2">{label}</span>
    </div>
  )
}
