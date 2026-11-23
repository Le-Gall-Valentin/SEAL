import { useId } from 'react'
import { useMessages } from '@/shared/i18n'
import { DialogBody, DialogFooter, ModalDialog } from '@/shared/ui'
import { restoreKeysPageMessages } from '../i18n'

interface ResetIdentityConfirmProps {
  readonly open: boolean
  readonly onCancel: () => void
  readonly onConfirm: () => void
}

/**
 * KEY-34, ADR 0036 (point 3) : confirmation de la réinitialisation, dans le cadre de Dialogue-Suppression, sans
 * saisie ; l'interface annonce l'historique illisible avant que la nouvelle clé de récupération soit tirée.
 */
export function ResetIdentityConfirm({ open, onCancel, onConfirm }: ResetIdentityConfirmProps) {
  const t = useMessages(restoreKeysPageMessages)
  const textId = useId()
  return (
    <ModalDialog
      open={open}
      onClose={onCancel}
      closable
      title={t('confirmTitle')}
      subtitle={t('confirmSubtitle')}
      danger
      describedBy={textId}
    >
      <form
        onSubmit={(event) => {
          event.preventDefault()
          onConfirm()
        }}
      >
        <DialogBody>
          <p id={textId} className="m-0 text-[14.5px] leading-[1.45] text-ink-2">
            {t('confirmText')}
          </p>
        </DialogBody>
        <DialogFooter
          onCancel={onCancel}
          cancelable
          submitLabel={t('confirmSubmit')}
          canSubmit
          danger
        />
      </form>
    </ModalDialog>
  )
}
