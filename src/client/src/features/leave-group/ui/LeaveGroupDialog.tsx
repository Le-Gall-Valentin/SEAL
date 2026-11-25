import { useId } from 'react'
import { type Conversation, ConversationAlert } from '@/entities/conversation'
import { useMessages } from '@/shared/i18n'
import { isolate } from '@/shared/lib'
import { DialogBody, DialogFooter, ModalDialog } from '@/shared/ui'
import { leaveGroupMessages } from '../i18n'
import { useLeaveGroup } from '../model/useLeaveGroup'

interface LeaveGroupDialogProps {
  readonly conversation: Conversation
  readonly open: boolean
  /** `left` : l'appelant n'est plus membre, la conversation n'est plus à montrer. */
  readonly onClose: (left: boolean) => void
}

/** GRP-03 : confirmation du départ d'un groupe, dans le cadre de Dialogue-Suppression (ADR 0041, point 3). */
export function LeaveGroupDialog({ conversation, open, onClose }: LeaveGroupDialogProps) {
  const t = useMessages(leaveGroupMessages)
  const textId = useId()
  const departure = useLeaveGroup(() => {
    onClose(true)
  })
  const cancel = () => {
    departure.reset()
    onClose(false)
  }
  return (
    <ModalDialog
      open={open}
      onClose={cancel}
      closable={!departure.pending}
      title={t('title', { group: isolate(conversation.name ?? '') })}
      danger
      describedBy={textId}
    >
      <form
        onSubmit={(event) => {
          event.preventDefault()
          departure.leave(conversation.id)
        }}
      >
        <DialogBody>
          <p id={textId} className="m-0 text-[14.5px] leading-[1.45] text-ink-2">
            {t('text')}
          </p>
          <ConversationAlert failure={departure.failure} />
        </DialogBody>
        <DialogFooter
          onCancel={cancel}
          cancelable={!departure.pending}
          submitLabel={t('submit')}
          canSubmit={!departure.pending}
        />
      </form>
    </ModalDialog>
  )
}
