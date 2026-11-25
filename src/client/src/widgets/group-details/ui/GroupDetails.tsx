import { type ReactNode, useEffect, useId, useRef, useState } from 'react'
import { initialsOf } from '@/entities/account'
import {
  canAdd,
  canRemove,
  canRename,
  type Conversation,
  ConversationAvatar,
  type Member,
} from '@/entities/conversation'
import { tintOf, useFriends } from '@/entities/friend'
import { AddMembersDialog } from '@/features/add-group-members'
import { LeaveGroupDialog } from '@/features/leave-group'
import { RemoveMemberDialog } from '@/features/remove-group-member'
import { RenameGroupDialog } from '@/features/rename-group'
import { useMessages } from '@/shared/i18n'
import { isolate } from '@/shared/lib'
import { Avatar } from '@/shared/ui'
import { groupDetailsMessages } from '../i18n'

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

const DAY_MONTH = new Intl.DateTimeFormat('fr', { day: 'numeric', month: 'short' })

type Dialog = { readonly kind: 'add' } | { readonly kind: 'rename' } | { readonly kind: 'leave' }

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
  const friends = useFriends().data
  const friendIds = new Set(friends?.map((friend) => friend.userId))
  const [dialog, setDialog] = useState<Dialog | null>(null)
  const [removing, setRemoving] = useState<Member | null>(null)
  // WCAG 2.4.3 : la ligne retirée avait le focus ; il passe au titre des membres une fois la confirmation fermée, car
  // le reste de l'écran est inerte tant que la fenêtre modale est ouverte (comme l'écran Amis).
  const focusMembersOnClose = useRef(false)
  useEffect(() => {
    if (removing === null && focusMembersOnClose.current) {
      focusMembersOnClose.current = false
      membersRef.current?.focus()
    }
  }, [removing])
  const count = conversation.members.length
  const date = DAY_MONTH.format(conversation.createdAt)

  const content = (
    <>
      <div className="flex min-h-16 items-center justify-between gap-2 border-b border-line pr-2 pl-5">
        <h2 id={titleId} className="m-0 text-[16px] font-bold">
          {t('title')}
        </h2>
        <button
          type="button"
          aria-label={variant === 'aside' ? t('closePanel') : t('close')}
          onClick={onClose}
          className="grid size-11 place-items-center rounded-[10px] text-ink-2"
        >
          <CloseIcon />
        </button>
      </div>
      <div className="flex flex-col gap-5 overflow-y-auto px-5 py-5">
        <div className="flex flex-col items-center gap-2 text-center">
          <ConversationAvatar conversation={conversation} me={me} size={72} />
          <h3 className="m-0 text-[20px] font-bold tracking-[-0.015em] wrap-anywhere">
            {conversation.name}
          </h3>
          <p className="m-0 text-[13px] text-ink-3">
            {count === 1 ? t('metaOne', { date }) : t('meta', { count: String(count), date })}
          </p>
          {canRename(conversation, me) && (
            <LinkButton
              onClick={() => {
                setDialog({ kind: 'rename' })
              }}
            >
              <PencilIcon />
              {t('rename')}
            </LinkButton>
          )}
        </div>
        {variant === 'aside' && (
          <p className="m-0 rounded-[14px] bg-accent-soft px-3.5 py-3 text-[13px] leading-[1.45] text-ink-2">
            {t('encryption')}
          </p>
        )}
        <section className="flex flex-col gap-2" aria-labelledby={`${titleId}-members`}>
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
                  setDialog({ kind: 'add' })
                }}
              >
                <PersonPlusIcon />
                {t('add')}
              </LinkButton>
            )}
          </div>
          {variant === 'drawer' && (
            <p className="m-0 text-[13px] leading-[1.45] text-ink-3">{t('addHint')}</p>
          )}
          <ul className="m-0 flex list-none flex-col gap-1 p-0">
            {conversation.members.map((member) => {
              const isMe = member.userId === me
              return (
                <li key={member.userId} className="flex items-center gap-3 py-1.5">
                  <Avatar
                    initials={initialsOf(member.displayName)}
                    tint={tintOf(member.userId)}
                    size={40}
                  />
                  <div className="flex min-w-0 flex-1 flex-col">
                    <span className="flex flex-wrap items-center gap-1.5">
                      <span className="truncate text-[14.5px] font-semibold">
                        {isMe ? t('you') : member.displayName}
                      </span>
                      {member.role === 'ADMIN' && <Tag accent>{t('admin')}</Tag>}
                      {!isMe && friends !== undefined && !friendIds.has(member.userId) && (
                        <Tag>{t('notFriend')}</Tag>
                      )}
                    </span>
                    <span className="truncate font-mono text-[12px] text-ink-3">
                      @{member.username}
                    </span>
                  </div>
                  {canRemove(conversation, me, member.userId) && (
                    <button
                      type="button"
                      aria-label={t('remove', { name: isolate(member.displayName) })}
                      onClick={() => {
                        setRemoving(member)
                      }}
                      className="grid size-11 flex-none place-items-center rounded-[10px] text-ink-2 hover:bg-sunken"
                    >
                      <PersonMinusIcon />
                    </button>
                  )}
                </li>
              )
            })}
          </ul>
        </section>
        <div>
          <LinkButton
            danger
            onClick={() => {
              setDialog({ kind: 'leave' })
            }}
          >
            {t('leave')}
          </LinkButton>
        </div>
      </div>
      <RenameGroupDialog
        conversation={conversation}
        open={dialog?.kind === 'rename'}
        onClose={() => {
          setDialog(null)
        }}
      />
      <AddMembersDialog
        conversation={conversation}
        open={dialog?.kind === 'add'}
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
        open={dialog?.kind === 'leave'}
        onClose={(left) => {
          setDialog(null)
          if (left) {
            onLeft()
          }
        }}
      />
    </>
  )

  return variant === 'aside' ? (
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

/**
 * Tiroir de Conv-Tablette : `dialog` natif modal, 380 px à droite, voile --overlay ; feuille plein écran sur téléphone
 * (ADR 0041, point 3).
 */
function Drawer({
  open,
  onClose,
  titleId,
  children,
}: {
  readonly open: boolean
  readonly onClose: () => void
  readonly titleId: string
  readonly children: ReactNode
}) {
  const ref = useRef<HTMLDialogElement>(null)
  useEffect(() => {
    const drawer = ref.current
    if (drawer === null) {
      return
    }
    if (open && !drawer.open) {
      drawer.showModal()
    } else if (!open && drawer.open) {
      drawer.close()
    }
  }, [open])
  return (
    <dialog
      ref={ref}
      aria-labelledby={titleId}
      onCancel={(event) => {
        event.preventDefault()
        // Seul Échap adressé au tiroir le ferme, pas celui d'un dialogue ouvert depuis lui.
        if (event.target === event.currentTarget) {
          onClose()
        }
      }}
      className="ml-auto h-full max-h-none w-full max-w-full flex-col bg-raised p-0 text-ink shadow-dialog backdrop:bg-overlay open:flex sm:max-w-95"
    >
      {open && children}
    </dialog>
  )
}

/** Étiquette d'un membre : « Admin » accentuée, « Pas dans vos amis » neutre (Conv-Tablette). */
function Tag({
  accent = false,
  children,
}: {
  readonly accent?: boolean
  readonly children: ReactNode
}) {
  return (
    <span
      className={`rounded-full px-2 py-0.5 text-[11px] font-semibold ${
        accent ? 'bg-accent-soft text-accent-text' : 'text-ink-2 inset-ring inset-ring-line-strong'
      }`}
    >
      {children}
    </span>
  )
}

function LinkButton({
  danger = false,
  onClick,
  children,
}: {
  readonly danger?: boolean
  readonly onClick: () => void
  readonly children: ReactNode
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`inline-flex min-h-11 items-center gap-1.5 text-[14px] font-semibold ${
        danger ? 'text-danger' : 'text-accent-text'
      }`}
    >
      {children}
    </button>
  )
}

function CloseIcon() {
  return (
    <svg
      width="20"
      height="20"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.7"
      strokeLinecap="round"
      aria-hidden="true"
    >
      <path d="M6.5 6.5l11 11M17.5 6.5l-11 11" />
    </svg>
  )
}

function PencilIcon() {
  return (
    <svg
      width="16"
      height="16"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="M4 20h4L19 9l-4-4L4 16z" />
    </svg>
  )
}

function PersonPlusIcon() {
  return (
    <svg
      width="18"
      height="18"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.7"
      strokeLinecap="round"
      aria-hidden="true"
    >
      <circle cx="10" cy="8" r="3.5" />
      <path d="M3.5 19c.8-3.4 3.4-5.2 6.5-5.2s5.7 1.8 6.5 5.2M18.5 8v6M15.5 11h6" />
    </svg>
  )
}

function PersonMinusIcon() {
  return (
    <svg
      width="20"
      height="20"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.7"
      strokeLinecap="round"
      aria-hidden="true"
    >
      <circle cx="10" cy="8" r="3.5" />
      <path d="M3.5 19c.8-3.4 3.4-5.2 6.5-5.2s5.7 1.8 6.5 5.2M15.5 11h6" />
    </svg>
  )
}
