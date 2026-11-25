import { useState } from 'react'
import {
  type Conversation,
  ConversationAlert,
  GroupNameField,
  isGroupNameValid,
} from '@/entities/conversation'
import { useMessages } from '@/shared/i18n'
import { DialogBody, DialogFooter, ModalDialog } from '@/shared/ui'
import { renameGroupMessages } from '../i18n'
import { useRenameGroup } from '../model/useRenameGroup'

interface RenameGroupDialogProps {
  readonly conversation: Conversation
  readonly open: boolean
  readonly onClose: () => void
}

/**
 * GRP-03 : « Renommer le groupe » (ADR 0041, point 3), réservé à l'administrateur. Le champ part du nom actuel ;
 * « Enregistrer » n'agit qu'après un changement valide.
 */
export function RenameGroupDialog({ conversation, open, onClose }: RenameGroupDialogProps) {
  const t = useMessages(renameGroupMessages)
  const current = conversation.name ?? ''
  // Brouillon : null tant que rien n'est saisi, pour repartir du nom actuel à chaque ouverture.
  const [draft, setDraft] = useState<string | null>(null)
  const [touched, setTouched] = useState(false)
  const name = draft ?? current
  const clear = () => {
    setDraft(null)
    setTouched(false)
  }
  const done = () => {
    clear()
    onClose()
  }
  const renaming = useRenameGroup(done)
  const cancel = () => {
    clear()
    renaming.reset()
    onClose()
  }
  const valid = isGroupNameValid(name)
  const invalid = (touched && name !== '' && !valid) || renaming.failure?.kind === 'invalid-name'
  const canSubmit = !renaming.pending && valid && name.normalize('NFC') !== current
  return (
    <ModalDialog open={open} onClose={cancel} closable={!renaming.pending} title={t('title')}>
      <form
        onSubmit={(event) => {
          event.preventDefault()
          if (canSubmit) {
            renaming.rename({ conversationId: conversation.id, name })
          }
        }}
      >
        <DialogBody>
          <GroupNameField
            value={name}
            onChange={setDraft}
            onBlur={() => {
              setTouched(true)
            }}
            invalid={invalid}
            disabled={renaming.pending}
          />
          <ConversationAlert failure={renaming.failure} />
        </DialogBody>
        <DialogFooter
          onCancel={cancel}
          cancelable={!renaming.pending}
          submitLabel={t('submit')}
          canSubmit={canSubmit}
        />
      </form>
    </ModalDialog>
  )
}
