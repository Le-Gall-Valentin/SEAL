import { type KeyboardEvent, useState } from 'react'
import {
  ConversationAlert,
  GroupNameField,
  isGroupNameValid,
  MAX_MEMBERS,
} from '@/entities/conversation'
import { type Friend, FriendPicker, useFriends } from '@/entities/friend'
import { useCreateGroup } from '@/features/create-group'
import { useStartDirect } from '@/features/start-direct-conversation'
import { useMessages } from '@/shared/i18n'
import { DialogBody, DialogFooter, ModalDialog } from '@/shared/ui'
import { newConversationMessages } from '../i18n'

type Kind = 'direct' | 'group'

const KINDS: readonly Kind[] = ['direct', 'group']

/** GRP-01 : 50 membres au plus, le créateur compris. */
const MAX_NEWCOMERS = MAX_MEMBERS - 1

interface NewConversationDialogProps {
  readonly open: boolean
  readonly onClose: () => void
  /** La conversation ouverte ou créée, à montrer. */
  readonly onOpened: (conversationId: string) => void
}

/**
 * Maquette Nouvelle-Conversation (CNV-01, CNV-03, GRP-01, GRP-02, FRD-20) : « Message direct » ouvre la directe avec un
 * ami (ADR 0041, point 3) ; « Groupe » crée un groupe nommé avec ses membres, le créateur compté dans « Créer le groupe
 * (n) ». Le sélecteur ne propose que des amis ; le serveur revérifie (FRD-21).
 */
export function NewConversationDialog({ open, onClose, onOpened }: NewConversationDialogProps) {
  const t = useMessages(newConversationMessages)
  // La fenêtre reste montée avec l'écran : la liste d'amis ne se lit qu'à son ouverture.
  const friendList = useFriends({ enabled: open })
  const friends = friendList.data ?? []
  const [kind, setKind] = useState<Kind>('direct')
  const [chosen, setSelected] = useState<readonly Friend[]>([])
  // FRD-21 : un ami retiré entre-temps quitte la sélection dès que la liste d'amis relue ne le compte plus.
  const selected =
    friendList.data === undefined
      ? chosen
      : chosen.filter((friend) => friends.some((current) => current.userId === friend.userId))
  const [name, setName] = useState('')
  const [nameTouched, setNameTouched] = useState(false)

  function resetForm() {
    setKind('direct')
    setSelected([])
    setName('')
    setNameTouched(false)
  }
  const opened = (conversationId: string) => {
    resetForm()
    onOpened(conversationId)
  }
  const direct = useStartDirect(opened)
  const group = useCreateGroup(opened)
  const pending = direct.pending || group.pending
  const failure = kind === 'direct' ? direct.failure : group.failure
  const nameInvalid =
    (nameTouched && name !== '' && !isGroupNameValid(name)) || group.refusedName === name
  const canSubmit =
    !pending &&
    (kind === 'direct' ? selected.length === 1 : selected.length > 0 && isGroupNameValid(name))

  function choose(next: Kind) {
    if (next !== kind) {
      setKind(next)
      setSelected([])
      direct.reset()
      group.reset()
    }
  }

  function onKindKey(event: KeyboardEvent<HTMLButtonElement>) {
    // Groupe de boutons radio (WAI-ARIA) : les flèches passent d'une option à l'autre et la choisissent.
    if (['ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown'].includes(event.key)) {
      event.preventDefault()
      const next = kind === 'direct' ? 'group' : 'direct'
      choose(next)
      event.currentTarget.parentElement
        ?.querySelector<HTMLButtonElement>(`[data-kind="${next}"]`)
        ?.focus()
    }
  }

  const cancel = () => {
    resetForm()
    direct.reset()
    group.reset()
    onClose()
  }

  return (
    <ModalDialog open={open} onClose={cancel} closable={!pending} title={t('title')} wide sheet>
      <form
        onSubmit={(event) => {
          event.preventDefault()
          if (!canSubmit) {
            return
          }
          const [first] = selected
          if (kind === 'direct' && first) {
            direct.open(first.userId)
          } else if (kind === 'group') {
            group.create({ name, memberIds: selected.map((friend) => friend.userId) })
          }
        }}
      >
        <DialogBody>
          <div
            role="radiogroup"
            aria-label={t('kind')}
            className="grid grid-cols-2 gap-0.5 rounded-xl bg-sunken p-0.75 inset-ring inset-ring-line"
          >
            {KINDS.map((option) => (
              <button
                key={option}
                type="button"
                role="radio"
                data-kind={option}
                aria-checked={kind === option}
                tabIndex={kind === option ? 0 : -1}
                disabled={pending}
                onClick={() => {
                  choose(option)
                }}
                onKeyDown={onKindKey}
                className={`h-9.5 rounded-lg font-sans text-[13px] font-semibold ${
                  kind === option
                    ? 'bg-surface text-ink shadow-segment inset-ring inset-ring-line'
                    : 'text-ink-2'
                }`}
              >
                {t(option)}
              </button>
            ))}
          </div>
          {kind === 'group' && (
            <GroupNameField
              value={name}
              onChange={setName}
              onBlur={() => {
                setNameTouched(true)
              }}
              invalid={nameInvalid}
              disabled={pending}
            />
          )}
          <FriendPicker
            // Un autre onglet repart d'un sélecteur vide : saisie comprise.
            key={kind}
            label={kind === 'direct' ? t('direct') : t('members')}
            labelHidden={kind === 'direct'}
            friends={friends}
            loading={friendList.data === undefined && !friendList.isError}
            failed={friendList.data === undefined && friendList.isError}
            onRetry={() => {
              void friendList.refetch()
            }}
            selected={selected}
            exclude={new Set()}
            max={kind === 'direct' ? 1 : MAX_NEWCOMERS}
            onSelect={(friend) => {
              setSelected((current) => [...current, friend])
            }}
            onRemove={(friend) => {
              setSelected((current) => current.filter((item) => item.userId !== friend.userId))
            }}
          />
          <ConversationAlert failure={failure} />
        </DialogBody>
        <DialogFooter
          onCancel={cancel}
          cancelable={!pending}
          submitLabel={
            kind === 'direct' ? t('open') : t('create', { count: String(selected.length + 1) })
          }
          canSubmit={canSubmit}
        />
      </form>
    </ModalDialog>
  )
}
