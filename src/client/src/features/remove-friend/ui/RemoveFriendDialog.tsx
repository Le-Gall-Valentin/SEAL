import { useId } from 'react'
import type { Friend } from '@/entities/friend'
import { useMessages } from '@/shared/i18n'
import { DialogBody, DialogFooter, FormAlert, ModalDialog } from '@/shared/ui'
import { removeFriendMessages } from '../i18n'
import { useRemoveFriend } from '../model/useRemoveFriend'

interface RemoveFriendDialogProps {
  /** L'ami à retirer ; `null` ferme la fenêtre, qui reste montée pour rendre le focus. */
  readonly friend: Friend | null
  /** `removed` : l'ami a été retiré, la ligne qui avait le focus a disparu. */
  readonly onClose: (removed: boolean) => void
}

/** FRD-11, ADR 0038 (point 2) : confirmation du retrait, dans le cadre de Dialogue-Suppression. */
export function RemoveFriendDialog({ friend, onClose }: RemoveFriendDialogProps) {
  const t = useMessages(removeFriendMessages)
  const textId = useId()
  const removal = useRemoveFriend(() => {
    onClose(true)
  })
  const cancel = () => {
    onClose(false)
  }
  return (
    <ModalDialog
      open={friend !== null}
      onClose={cancel}
      closable={!removal.pending}
      title={t('title', { name: friend?.displayName ?? '' })}
      danger
      describedBy={textId}
    >
      <form
        onSubmit={(event) => {
          event.preventDefault()
          if (friend !== null) {
            removal.remove(friend.userId)
          }
        }}
      >
        <DialogBody>
          <p id={textId} className="m-0 text-[14.5px] leading-[1.45] text-ink-2">
            {t('text')}
          </p>
          <FormAlert failure={removal.formFailure} />
        </DialogBody>
        <DialogFooter
          onCancel={cancel}
          cancelable={!removal.pending}
          submitLabel={t('submit')}
          canSubmit={!removal.pending}
        />
      </form>
    </ModalDialog>
  )
}
