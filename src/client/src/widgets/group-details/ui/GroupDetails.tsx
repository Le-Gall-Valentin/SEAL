import { useEffect, useId, useRef, useState } from 'react'
import {
  canAdd,
  canRemove,
  canRename,
  type Conversation,
  type Member,
} from '@/entities/conversation'
import { useFriends } from '@/entities/friend'
import { AddMembersDialog } from '@/features/add-group-members'
import { LeaveGroupDialog } from '@/features/leave-group'
import { RemoveMemberDialog } from '@/features/remove-group-member'
import { RenameGroupDialog } from '@/features/rename-group'
import { useMessages } from '@/shared/i18n'
import { CrossIcon, LoadErrorState } from '@/shared/ui'
import { groupDetailsMessages } from '../i18n'
import { Drawer } from './Drawer'
import { GroupSummary } from './GroupSummary'
import { LeaveIcon, PersonPlusIcon, ShieldIcon } from './icons'
import { MemberRow } from './MemberRow'
import { LinkButton } from './parts'

interface GroupDetailsProps {
  readonly me: string
  readonly conversation: Conversation
  /** Aside de Conv-Ordinateur dès 1024 px ; tiroir modal de Conv-Tablette en dessous (ADR 0041, point 3). */
  readonly variant: 'aside' | 'drawer'
  /** Tiroir ouvert ; l'aside, lui, n'est monté que lorsqu'il est ouvert. */
  readonly open: boolean
  readonly onClose: () => void
  /** L'appelant a quitté le groupe : la conversation n'est plus à montrer. */
  readonly onLeft: () => void
}

type Dialog = 'add' | 'rename' | 'leave'

/**
 * GRP-03, GRP-04 : infos d'un groupe (Conv-Ordinateur, Conv-Tablette). Tout membre ajoute ses amis et part ; seul
 * l'administrateur renomme et retire, jamais lui-même. Un membre qui n'est pas un ami le dit (« Pas dans vos amis »).
 * Empreintes et état des clés attendent leur lot (ADR 0041, point 7).
 */
export function GroupDetails({
  me,
  conversation,
  variant,
  open,
  onClose,
  onLeft,
}: GroupDetailsProps) {
  const t = useMessages(groupDetailsMessages)
  const titleId = useId()
  const membersRef = useRef<HTMLHeadingElement>(null)
  const friends = useFriends()
  const [dialog, setDialog] = useState<Dialog | null>(null)
  const [removing, setRemoving] = useState<Member | null>(null)
  // WCAG 2.4.3 : quand le déclencheur d'un dialogue a disparu (ligne retirée, « Renommer » après un rôle perdu), le
  // focus passe au titre des membres une fois le dialogue fermé, car le reste de l'écran est inerte jusque-là.
  const focusMembersOnClose = useRef(false)
  useEffect(() => {
    if (dialog === null && removing === null && focusMembersOnClose.current) {
      focusMembersOnClose.current = false
      membersRef.current?.focus()
    }
  }, [dialog, removing])
  const count = conversation.members.length
  // A10, GRP-04 : tant que la liste d'amis n'est pas lue, aucun membre n'est montré, pour qu'aucun n'ait l'air d'un ami.
  const friendIds =
    friends.data === undefined ? null : new Set(friends.data.map((friend) => friend.userId))
  const aside = variant === 'aside'

  const content = (
    <>
      <div className="flex min-h-16 items-center gap-2 border-b border-line py-2 pr-2 pl-5">
        <h2 id={titleId} className="m-0 flex-1 text-[16px] font-bold">
          {t('title')}
        </h2>
        <button
          type="button"
          aria-label={aside ? t('closePanel') : t('close')}
          onClick={onClose}
          className="grid size-11 place-items-center rounded-[10px] text-ink-2"
        >
          <CrossIcon />
        </button>
      </div>
      <div className="flex flex-col gap-5 overflow-y-auto p-5">
        <GroupSummary
          me={me}
          conversation={conversation}
          onRename={
            canRename(conversation, me)
              ? () => {
                  setDialog('rename')
                }
              : undefined
          }
        />
        {aside && (
          <p className="m-0 flex gap-2.5 rounded-[14px] border border-line bg-accent-soft px-3 py-2.5 text-[13px] leading-[1.4] text-accent-text">
            <ShieldIcon />
            {t('encryption')}
          </p>
        )}
        <section className="flex flex-col gap-1.5" aria-labelledby={`${titleId}-members`}>
          <div className="flex items-center justify-between gap-2">
            <h3
              id={`${titleId}-members`}
              ref={membersRef}
              tabIndex={-1}
              className="m-0 text-[12px] font-bold tracking-[0.08em] text-ink-3 uppercase outline-none"
            >
              {count === 1 ? t('membersOne') : t('members', { count: String(count) })}
            </h3>
            {canAdd(conversation) && (
              <LinkButton
                onClick={() => {
                  setDialog('add')
                }}
              >
                {aside && <PersonPlusIcon />}
                {t('add')}
              </LinkButton>
            )}
          </div>
          {!aside && <p className="m-0 text-[13px] leading-[1.4] text-ink-3">{t('addHint')}</p>}
          {friendIds !== null ? (
            <ul className="m-0 flex list-none flex-col p-0">
              {conversation.members.map((member) => (
                <MemberRow
                  key={member.userId}
                  member={member}
                  isMe={member.userId === me}
                  friend={friendIds.has(member.userId)}
                  onRemove={
                    canRemove(conversation, me, member.userId)
                      ? () => {
                          setRemoving(member)
                        }
                      : undefined
                  }
                />
              ))}
            </ul>
          ) : (
            friends.isError && (
              <LoadErrorState
                heading="h3"
                onRetry={() => {
                  void friends.refetch()
                }}
              />
            )
          )}
        </section>
        <button
          type="button"
          onClick={() => {
            setDialog('leave')
          }}
          className="inline-flex min-h-11 items-center gap-2 self-center text-[14px] font-semibold text-danger"
        >
          <LeaveIcon />
          {t('leave')}
        </button>
      </div>
      <RenameGroupDialog
        conversation={conversation}
        open={dialog === 'rename'}
        onClose={() => {
          // Un refus FORBIDDEN relit le groupe : « Renommer » a pu disparaître avec le rôle.
          focusMembersOnClose.current = !canRename(conversation, me)
          setDialog(null)
        }}
      />
      <AddMembersDialog
        conversation={conversation}
        open={dialog === 'add'}
        onClose={() => {
          setDialog(null)
        }}
      />
      <RemoveMemberDialog
        conversation={conversation}
        member={removing}
        onClose={(removed) => {
          focusMembersOnClose.current = removed
          setRemoving(null)
        }}
      />
      <LeaveGroupDialog
        conversation={conversation}
        open={dialog === 'leave'}
        onClose={(left) => {
          setDialog(null)
          if (left) {
            onLeft()
          }
        }}
      />
    </>
  )

  return aside ? (
    <aside
      aria-labelledby={titleId}
      className="flex w-85 flex-none flex-col border-l border-line bg-surface"
    >
      {content}
    </aside>
  ) : (
    <Drawer open={open} onClose={onClose} titleId={titleId}>
      {content}
    </Drawer>
  )
}
