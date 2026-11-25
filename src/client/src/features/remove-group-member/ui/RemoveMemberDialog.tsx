import { useId } from 'react'
import { type Conversation, ConversationAlert, type Member } from '@/entities/conversation'
import { useMessages } from '@/shared/i18n'
import { isolate } from '@/shared/lib'
import { DialogBody, DialogFooter, ModalDialog } from '@/shared/ui'
import { removeMemberMessages } from '../i18n'
import { useRemoveMember } from '../model/useRemoveMember'

interface RemoveMemberDialogProps {
  readonly conversation: Conversation
  /** Le membre à retirer ; `null` ferme la fenêtre, qui reste montée pour rendre le focus. */
  readonly member: Member | null
  /** `removed` : le membre est parti, sa ligne qui avait le focus a disparu. */
  readonly onClose: (removed: boolean) => void
}

/** GRP-03 : confirmation du retrait d'un membre, dans le cadre de Dialogue-Suppression (ADR 0041, point 3). */
export function RemoveMemberDialog({ conversation, member, onClose }: RemoveMemberDialogProps) {
  const t = useMessages(removeMemberMessages)
  const textId = useId()
  const name = isolate(member?.displayName ?? '')
  const removal = useRemoveMember(() => {
    onClose(true)
  })
  const cancel = () => {
    removal.reset()
    onClose(false)
  }
  return (
    <ModalDialog
      open={member !== null}
      onClose={cancel}
      closable={!removal.pending}
      title={t('title', { name })}
      danger
      describedBy={textId}
    >
      <form
        onSubmit={(event) => {
          event.preventDefault()
          if (member !== null) {
            removal.remove({ conversationId: conversation.id, userId: member.userId })
          }
        }}
      >
        <DialogBody>
          <p id={textId} className="m-0 text-[14.5px] leading-[1.45] text-ink-2">
            {t('text', { name })}
          </p>
          <ConversationAlert failure={removal.failure} />
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
