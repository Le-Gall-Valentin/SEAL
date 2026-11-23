import { useId } from 'react'
import type { Friend } from '@/entities/friend'
import { useMessages } from '@/shared/i18n'
import { DialogBody, DialogFooter, FormAlert, ModalDialog } from '@/shared/ui'
import { removeFriendMessages } from '../i18n'
import { useRemoveFriend } from '../model/useRemoveFriend'

interface RemoveFriendDialogProps {
  readonly friend: Friend
  readonly onClose: () => void
}

/** FRD-11, ADR 0038 (point 2) : confirmation du retrait, dans le cadre de Dialogue-Suppression. */
export function RemoveFriendDialog({ friend, onClose }: RemoveFriendDialogProps) {
  const t = useMessages(removeFriendMessages)
  const textId = useId()
  const removal = useRemoveFriend(onClose)
  return (
    <ModalDialog
      open
      onClose={onClose}
      closable={!removal.pending}
      title={t('title', { name: friend.displayName })}
      danger
      describedBy={textId}
    >
      <form
        onSubmit={(event) => {
          event.preventDefault()
          removal.remove(friend.userId)
        }}
      >
        <DialogBody>
          <p id={textId} className="m-0 text-[14.5px] leading-[1.45] text-ink-2">
            {t('text')}
          </p>
          <FormAlert failure={removal.formFailure} />
        </DialogBody>
        <DialogFooter
          onCancel={onClose}
          cancelable={!removal.pending}
          submitLabel={t('submit')}
          canSubmit={!removal.pending}
        />
      </form>
    </ModalDialog>
  )
}
