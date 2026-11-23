import { useId, useState } from 'react'
import { useMessages } from '@/shared/i18n'
import { restoreKeysPageMessages } from '../i18n'
import { ResetIdentityConfirm } from './ResetIdentityConfirm'

interface LostRecoveryKeyProps {
  /** La réinitialisation est confirmée : la page laisse la place à Dialogue-Cle-Recuperation. */
  readonly onConfirmed: () => void
}

/** KEY-34, ADR 0036 (point 3) : « Clé de récupération perdue ? » de Restauration-Appareil, et sa confirmation. */
export function LostRecoveryKey({ onConfirmed }: LostRecoveryKeyProps) {
  const t = useMessages(restoreKeysPageMessages)
  const headingId = useId()
  const [confirming, setConfirming] = useState(false)
  return (
    <section aria-labelledby={headingId} className="flex flex-col gap-2 border-t border-line pt-4">
      <h2 id={headingId} className="m-0 text-[14.5px] font-[650]">
        {t('lostTitle')}
      </h2>
      <p className="m-0 text-[13px] leading-[1.45] text-ink-2">{t('lostText')}</p>
      <button
        type="button"
        className="mt-1 h-11 self-start rounded-[10px] bg-transparent px-3.5 font-sans text-[14px] font-semibold text-danger inset-ring inset-ring-danger-line"
        onClick={() => {
          setConfirming(true)
        }}
      >
        {t('reset')}
      </button>
      <ResetIdentityConfirm
        open={confirming}
        onCancel={() => {
          setConfirming(false)
        }}
        onConfirm={() => {
          setConfirming(false)
          onConfirmed()
        }}
      />
    </section>
  )
}
