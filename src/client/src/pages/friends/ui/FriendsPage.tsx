import { useEffect, useRef, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router'
import { ConversationAlert } from '@/entities/conversation'
import { type Friend, useFriends, useReceivedRequests, useSentRequests } from '@/entities/friend'
import { AnswerAlerts, useAnswerFriendRequest } from '@/features/answer-friend-request'
import { RemoveFriendDialog } from '@/features/remove-friend'
import { AddFriendForm, AddFriendSheet, PersonPlusIcon } from '@/features/send-friend-request'
import { useStartDirect } from '@/features/start-direct-conversation'
import { conversationPath, friendsTabOf } from '@/shared/config'
import { useMessages } from '@/shared/i18n'
import { LARGE_SCREEN, useMediaQuery } from '@/shared/lib'
import { LoadErrorState } from '@/shared/ui'
import { friendsPageMessages } from '../i18n'
import { FriendList } from './FriendList'
import { FriendsTabs } from './FriendsTabs'
import { InvitationsPanel } from './InvitationsPanel'

/**
 * Écran Amis (Amis-Ordinateur, Amis-Tablette, Amis-Telephone ; ADR 0038) : onglets Amis et Invitations ; dès
 * 1024 px, l'aside « Ajouter un ami » ; en dessous, le bouton rond et la feuille (point 4).
 */
export function FriendsPage() {
  const t = useMessages(friendsPageMessages)
  const large = useMediaQuery(LARGE_SCREEN)
  const [params] = useSearchParams()
  const tab = friendsTabOf(params)
  const friends = useFriends()
  const received = useReceivedRequests()
  const sent = useSentRequests()
  const answers = useAnswerFriendRequest()
  const navigate = useNavigate()
  // CNV-01, ADR 0038 (point 3) : « Envoyer un message » ouvre la directe, trouvée ou créée (ADR 0041, point 1).
  const direct = useStartDirect((conversationId) => {
    void navigate(conversationPath(conversationId))
  })
  const [sheetOpen, setSheetOpen] = useState(false)
  const [removing, setRemoving] = useState<Friend | null>(null)
  const field = useRef<HTMLInputElement>(null)
  const title = useRef<HTMLHeadingElement>(null)
  // WCAG 2.4.3 : la ligne retirée avait le focus ; il passe au titre une fois la confirmation fermée, car la page
  // reste inerte tant que la fenêtre modale est ouverte (ses effets, ceux d'un enfant, la ferment avant celui-ci).
  const focusTitleOnClose = useRef(false)
  useEffect(() => {
    if (removing === null && focusTitleOnClose.current) {
      focusTitleOnClose.current = false
      title.current?.focus()
    }
  }, [removing])
  const openAdd = () => {
    if (large) {
      field.current?.focus()
    } else {
      setSheetOpen(true)
    }
  }
  return (
    <main className="mx-auto flex w-full max-w-184 flex-col gap-4 px-4 pt-4 pb-6 sm:px-8 sm:pt-6 sm:pb-12 lg:max-w-288 lg:px-10 lg:pt-8">
      <header className="flex min-h-11 items-center justify-between gap-3">
        <h1
          ref={title}
          tabIndex={-1}
          className="m-0 text-[26px] font-bold tracking-[-0.025em] outline-none"
        >
          {t('title')}
        </h1>
        {!large && (
          <button
            type="button"
            aria-label={t('addFriend')}
            aria-haspopup="dialog"
            aria-expanded={sheetOpen}
            onClick={() => {
              setSheetOpen(true)
            }}
            className="grid size-11 place-items-center rounded-full bg-accent text-on-accent hover:bg-accent-hover"
          >
            <PersonPlusIcon />
          </button>
        )}
      </header>
      <FriendsTabs
        tab={tab}
        friendCount={friends.data?.length}
        receivedCount={received.data?.length ?? 0}
      />
      <AnswerAlerts gone={answers.gone} failure={answers.formFailure} />
      <ConversationAlert failure={direct.failure} />
      <div className={large ? 'flex items-start gap-8' : 'flex flex-col'}>
        <div className="min-w-0 flex-1">
          {tab === 'invitations' ? (
            (received.isError && received.data === undefined) ||
            (sent.isError && sent.data === undefined) ? (
              <LoadErrorState
                heading="h2"
                onRetry={() => {
                  void received.refetch()
                  void sent.refetch()
                }}
              />
            ) : (
              received.data !== undefined &&
              sent.data !== undefined && (
                <InvitationsPanel
                  received={received.data}
                  sent={sent.data}
                  busyId={answers.busyId}
                  onAccept={answers.accept}
                  onDecline={answers.decline}
                  onRevoke={answers.revoke}
                />
              )
            )
          ) : friends.isError && friends.data === undefined ? (
            <LoadErrorState heading="h2" onRetry={() => void friends.refetch()} />
          ) : (
            friends.data !== undefined && (
              <FriendList
                friends={friends.data}
                onAdd={openAdd}
                onMessage={(friend) => {
                  direct.open(friend.userId)
                }}
                onRemove={setRemoving}
              />
            )
          )}
        </div>
        {large && (
          <aside aria-label={t('addFriend')} className="sticky top-4 w-88 flex-none">
            <section className="flex flex-col gap-3.5 rounded-[18px] bg-surface p-5 inset-ring inset-ring-line">
              <h2 className="m-0 text-[16px] font-bold">{t('addFriend')}</h2>
              <AddFriendForm
                onAccept={answers.acceptNow}
                answering={answers.busyId !== null}
                inputRef={field}
              />
            </section>
          </aside>
        )}
      </div>
      {!large && (
        <AddFriendSheet
          open={sheetOpen}
          onClose={() => {
            setSheetOpen(false)
          }}
          onAccept={answers.acceptNow}
          answering={answers.busyId !== null}
        />
      )}
      <RemoveFriendDialog
        friend={removing}
        onClose={(removed) => {
          focusTitleOnClose.current = removed
          setRemoving(null)
        }}
      />
    </main>
  )
}
