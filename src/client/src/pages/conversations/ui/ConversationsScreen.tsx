import { useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router'
import {
  ConversationAlert,
  conversationFailureOf,
  counterpartOf,
  useConversation,
} from '@/entities/conversation'
import { conversationOf, conversationPath, ROUTES } from '@/shared/config'
import { LARGE_SCREEN, useMediaQuery, WIDE_SCREEN } from '@/shared/lib'
import { ConversationList } from '@/widgets/conversation-list'
import { ConversationPanel } from '@/widgets/conversation-panel'
import { GroupDetails } from '@/widgets/group-details'
import { NewConversationDialog } from '@/widgets/new-conversation'

/**
 * CNV-04 : écran Conversations (Conv-Ordinateur, Conv-Tablette, Conv-Telephone-Liste, Conv-Telephone-Fil). La
 * conversation ouverte est dans l'adresse, `/?conversation={id}` (ADR 0041, point 1). Ordinateur et tablette montrent
 * la liste et le fil côte à côte ; le téléphone, l'un ou l'autre. Une conversation disparue (CNV-06), ou une directe
 * dont l'autre compte n'est plus actif, ramène à la liste avec son alerte (ADR 0041, point 4).
 */
export function ConversationsScreen({ me }: { readonly me: string }) {
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const large = useMediaQuery(LARGE_SCREEN)
  const wide = useMediaQuery(WIDE_SCREEN)
  const variant = large ? 'large' : wide ? 'medium' : 'small'
  const selectedId = conversationOf(params)
  const detail = useConversation(selectedId)
  const [creating, setCreating] = useState(false)
  // Conv-Ordinateur montre les infos ouvertes ; le tiroir de Conv-Tablette s'ouvre à la demande.
  const [asideOpen, setAsideOpen] = useState(true)
  const [drawerOpen, setDrawerOpen] = useState(false)

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
          {gone && (
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
          />
        </div>
      )}
      {showPanel && (
        <div className="flex min-h-0 min-w-0 flex-1 flex-col">
          {failure !== null && !gone && (
            <div className="px-4 pt-4">
              <ConversationAlert failure={failure} />
            </div>
          )}
          {!loading && (
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
