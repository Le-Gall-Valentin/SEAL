import { useMessages } from '@/shared/i18n'
import { registerMessages } from '../i18n'

/** AUTH-03 : l'identifiant est définitif, annoncé avant la création du compte (Inscription-Telephone). */
export function FinalUsernameNotice() {
  const t = useMessages(registerMessages)
  return (
    <div className="flex gap-3 rounded-[14px] border border-warning-line bg-warning-soft px-3.5 py-3 text-[13px] leading-[1.35]">
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
        className="mt-px flex-none text-warning"
      >
        <rect x="5" y="10.5" width="14" height="10" rx="2.5" />
        <path d="M8 10.5v-3a4 4 0 0 1 8 0v3" />
      </svg>
      <div className="flex flex-col gap-1">
        <span className="font-semibold text-warning">{t('finalTitle')}</span>
        <span className="text-ink-2">{t('finalBody')}</span>
      </div>
    </div>
  )
}
