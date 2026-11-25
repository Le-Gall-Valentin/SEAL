import { useEffect, useRef, useState } from 'react'
import { useLocation, useNavigate, useSearchParams } from 'react-router'
import {
  ConversationAlert,
  conversationFailureOf,
  counterpartOf,
  useConversation,
} from '@/entities/conversation'
import { conversationOf, conversationPath, ROUTES } from '@/shared/config'
import { useMessages } from '@/shared/i18n'
import { LARGE_SCREEN, useMediaQuery, WIDE_SCREEN } from '@/shared/lib'
import { LoadErrorState } from '@/shared/ui'
import { ConversationList } from '@/widgets/conversation-list'
import { ConversationPanel } from '@/widgets/conversation-panel'
import { GroupDetails } from '@/widgets/group-details'
import { NewConversationDialog } from '@/widgets/new-conversation'
import { conversationsPageMessages } from '../i18n'

/** État d'historique porté par le retour à la liste : la conversation demandée a disparu (ADR 0041, point 4). */
const GONE_STATE = { conversationGone: true } as const

function isGoneState(state: unknown): boolean {
  return (
    typeof state === 'object' &&
    state !== null &&
    'conversationGone' in state &&
    state.conversationGone === true
  )
}

/**
 * CNV-04 : écran Conversations (Conv-Ordinateur, Conv-Tablette, Conv-Telephone-Liste, Conv-Telephone-Fil). La
 * conversation ouverte est dans l'adresse, `/?conversation={id}` (ADR 0041, point 1). Ordinateur et tablette montrent
 * la liste et le fil côte à côte ; le téléphone, l'un ou l'autre. Une conversation disparue (CNV-06), ou une directe
 * dont l'autre compte n'est plus actif, ramène à la liste avec son alerte (ADR 0041, point 4).
 */
export function ConversationsScreen({ me }: { readonly me: string }) {
  const t = useMessages(conversationsPageMessages)
  const navigate = useNavigate()
  const location = useLocation()
  const [params] = useSearchParams()
  const large = useMediaQuery(LARGE_SCREEN)
  const wide = useMediaQuery(WIDE_SCREEN)
  const variant = large ? 'large' : wide ? 'medium' : 'small'
  const requested = conversationOf(params)
  const [creating, setCreating] = useState(false)
  // Conv-Ordinateur montre les infos ouvertes ; le tiroir de Conv-Tablette s'ouvre à la demande.
  const [asideOpen, setAsideOpen] = useState(true)
  const [drawerOpen, setDrawerOpen] = useState(false)
  // GRP-03 : le groupe que l'utilisateur vient de quitter n'est pas « disparu » : son 404 est attendu, et l'adresse
  // revient à la liste par une transition que la relecture peut devancer.
  const [leftId, setLeftId] = useState<string | null>(null)
  // Une fois l'adresse revenue à la liste, le groupe quitté redevient ouvrable (s'il y est réinvité).
  if (requested === null && leftId !== null) {
    setLeftId(null)
  }
  const selectedId = requested !== null && requested === leftId ? null : requested
  const detail = useConversation(selectedId)

  const failure = detail.error === null ? null : conversationFailureOf(detail.error)
  const opened = detail.data
  const gone =
    selectedId !== null &&
    (failure?.kind === 'gone' ||
      (opened?.type === 'DIRECT' && counterpartOf(opened, me) === undefined))
  const conversation = selectedId === null || gone ? null : (opened ?? null)
  const loading = selectedId !== null && !gone && conversation === null && failure === null
  const infoOpen = variant === 'large' ? asideOpen : drawerOpen
  const showList = variant !== 'small' || selectedId === null || gone
  const showPanel = variant !== 'small' || (selectedId !== null && !gone)
  const notice = gone || isGoneState(location.state)

  // CNV-06 : l'adresse d'une conversation disparue revient à la liste, l'alerte portée par l'historique ; sur
  // téléphone, la barre du bas reparaît (ADR 0041, point 4).
  useEffect(() => {
    if (gone) {
      void navigate(ROUTES.HOME, { replace: true, state: GONE_STATE })
    }
  }, [gone, navigate])

  // WCAG 2.4.3 : après un départ, le focus va au titre de la liste, une fois la conversation fermée.
  const listTitle = useRef<HTMLHeadingElement>(null)
  const focusListTitle = useRef(false)
  useEffect(() => {
    if (selectedId === null && focusListTitle.current) {
      focusListTitle.current = false
      listTitle.current?.focus()
    }
  }, [selectedId])

  const toList = () => {
    void navigate(ROUTES.HOME)
  }
  const closeInfo = () => {
    if (variant === 'large') {
      setAsideOpen(false)
    } else {
      setDrawerOpen(false)
    }
  }

  return (
    <main className="flex min-h-0 flex-1">
      {showList && (
        <div
          className={`flex min-h-0 flex-col bg-surface ${
            variant === 'large'
              ? 'w-85 flex-none border-r border-line'
              : variant === 'medium'
                ? 'w-75 flex-none border-r border-line'
                : 'flex-1'
          }`}
        >
          {notice && (
            <div className="px-4 pt-4">
              <ConversationAlert failure={{ kind: 'gone' }} />
            </div>
          )}
          <ConversationList
            me={me}
            selectedId={conversation?.id ?? null}
            onNew={() => {
              setCreating(true)
            }}
            variant={variant}
            titleRef={listTitle}
          />
        </div>
      )}
      {showPanel && (
        <div className="flex min-h-0 min-w-0 flex-1 flex-col">
          {failure !== null && !gone ? (
            // A10 : un échec autre qu'une disparition se réessaie ; sur téléphone, « Retour » mène à la liste.
            <div className="flex flex-1 flex-col items-center justify-center gap-4 px-6">
              <LoadErrorState
                heading="h2"
                onRetry={() => {
                  void detail.refetch()
                }}
              />
              {variant === 'small' && (
                <button
                  type="button"
                  onClick={toList}
                  className="inline-flex h-11 items-center rounded-[10px] bg-surface px-4 text-[14.5px] font-semibold text-ink inset-ring inset-ring-line-strong"
                >
                  {t('back')}
                </button>
              )}
            </div>
          ) : (
            !loading && (
              <ConversationPanel
                me={me}
                conversation={conversation}
                variant={variant}
                infoOpen={infoOpen}
                onToggleInfo={() => {
                  if (variant === 'large') {
                    setAsideOpen((open) => !open)
                  } else {
                    setDrawerOpen((open) => !open)
                  }
                }}
                onBack={toList}
                onNew={() => {
                  setCreating(true)
                }}
              />
            )
          )}
        </div>
      )}
      {conversation?.type === 'GROUP' && (variant !== 'large' || asideOpen) && (
        <GroupDetails
          me={me}
          conversation={conversation}
          variant={variant === 'large' ? 'aside' : 'drawer'}
          open={infoOpen}
          onClose={closeInfo}
          onLeft={() => {
            closeInfo()
            setLeftId(conversation.id)
            focusListTitle.current = true
            toList()
          }}
        />
      )}
      <NewConversationDialog
        open={creating}
        onClose={() => {
          setCreating(false)
        }}
        onOpened={(conversationId) => {
          setCreating(false)
          void navigate(conversationPath(conversationId))
        }}
      />
    </main>
  )
}
