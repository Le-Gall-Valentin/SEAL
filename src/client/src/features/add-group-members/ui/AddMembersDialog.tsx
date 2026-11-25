import { useState } from 'react'
import { type Conversation, ConversationAlert, seatsLeft } from '@/entities/conversation'
import { type Friend, FriendPicker, useFriends } from '@/entities/friend'
import { useMessages } from '@/shared/i18n'
import { DialogBody, DialogFooter, ModalDialog } from '@/shared/ui'
import { addMembersMessages } from '../i18n'
import { useAddMembers } from '../model/useAddMembers'

interface AddMembersDialogProps {
  readonly conversation: Conversation
  readonly open: boolean
  readonly onClose: () => void
}

/**
 * GRP-03, GRP-04 : « Ajouter des membres » (ADR 0041, point 3) : le sélecteur de Nouvelle-Conversation, sans les
 * membres présents, borné aux places restantes (GRP-01).
 */
export function AddMembersDialog({ conversation, open, onClose }: AddMembersDialogProps) {
  const t = useMessages(addMembersMessages)
  const friendList = useFriends()
  const friends = friendList.data ?? []
  const [selected, setSelected] = useState<readonly Friend[]>([])
  const done = () => {
    setSelected([])
    onClose()
  }
  const admission = useAddMembers(done)
  const cancel = () => {
    setSelected([])
    admission.reset()
    onClose()
  }
  return (
    <ModalDialog open={open} onClose={cancel} closable={!admission.pending} title={t('title')} wide>
      <form
        onSubmit={(event) => {
          event.preventDefault()
          if (selected.length > 0) {
            admission.admit({
              conversationId: conversation.id,
              userIds: selected.map((friend) => friend.userId),
            })
          }
        }}
      >
        <DialogBody>
          <FriendPicker
            label={t('members')}
            friends={friends}
            loading={friendList.data === undefined && !friendList.isError}
            failed={friendList.data === undefined && friendList.isError}
            onRetry={() => {
              void friendList.refetch()
            }}
            selected={selected}
            exclude={new Set(conversation.members.map((member) => member.userId))}
            max={seatsLeft(conversation)}
            onSelect={(friend) => {
              setSelected((current) => [...current, friend])
            }}
            onRemove={(friend) => {
              setSelected((current) => current.filter((chosen) => chosen.userId !== friend.userId))
            }}
          />
          <ConversationAlert failure={admission.failure} />
        </DialogBody>
        <DialogFooter
          onCancel={cancel}
          cancelable={!admission.pending}
          submitLabel={t('submit', { count: String(selected.length) })}
          canSubmit={!admission.pending && selected.length > 0}
        />
      </form>
    </ModalDialog>
  )
}
