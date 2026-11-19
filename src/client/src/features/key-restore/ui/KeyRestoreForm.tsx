import type { KeyOwner } from '@/entities/keyring'
import { useMessages } from '@/shared/i18n'
import { Alert, FormAlert, PrimaryButton } from '@/shared/ui'
import { keyRestoreMessages } from '../i18n'
import { useKeyRestore } from '../model/useKeyRestore'
import { RecoveryKeyInput } from './RecoveryKeyInput'

interface KeyRestoreFormProps {
  readonly owner: KeyOwner
  /** Appelé une fois le trousseau restauré : la zone du compte complète la publication. */
  readonly onRestored: () => void
}

/** KEY-33 : formulaire et note de Restauration-Appareil (ADR 0029, points 5 et 6). */
export function KeyRestoreForm({ owner, onRestored }: KeyRestoreFormProps) {
  const t = useMessages(keyRestoreMessages)
  const restore = useKeyRestore(owner, onRestored)
  const failure = restore.failure
  return (
    <>
      <form
        className="flex flex-col gap-3.5"
        onSubmit={(event) => {
          event.preventDefault()
          restore.submit()
        }}
      >
        <RecoveryKeyInput
          groups={restore.groups}
          onEnter={restore.enter}
          disabled={restore.pending}
        />
        {failure?.kind === 'wrong-key' && <Alert variant="error">{t('wrongKey')}</Alert>}
        {failure?.kind === 'invalid' && <Alert variant="error">{t('invalidVault')}</Alert>}
        <FormAlert
          failure={failure?.kind === 'network' || failure?.kind === 'unexpected' ? failure : null}
        />
        <PrimaryButton disabled={!restore.canSubmit}>{t('submit')}</PrimaryButton>
      </form>
      <div className="flex items-center gap-2.5 rounded-[14px] bg-accent-soft px-3.5 py-3 text-[13px] leading-[1.4] text-ink-2 inset-ring inset-ring-accent-line">
        <svg
          width="18"
          height="18"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="1.8"
          strokeLinecap="round"
          strokeLinejoin="round"
          aria-hidden="true"
          className="flex-none text-accent-text"
        >
          <path d="M12 3l7.5 3v5.5c0 4.6-3.2 8.3-7.5 9.5-4.3-1.2-7.5-4.9-7.5-9.5V6z" />
          <path d="M8.8 12.2l2.2 2.2 4.3-4.4" />
        </svg>
        <span>{t('localOnly')}</span>
      </div>
    </>
  )
}
