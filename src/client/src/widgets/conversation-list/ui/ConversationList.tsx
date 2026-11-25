import { type Ref, useRef, useState } from 'react'
import { Link } from 'react-router'
import {
  type Conversation,
  ConversationAvatar,
  matchConversation,
  titleOf,
  useConversations,
} from '@/entities/conversation'
import { conversationPath } from '@/shared/config'
import { useMessages } from '@/shared/i18n'
import { isolate } from '@/shared/lib'
import { CrossIcon, LoadErrorState, Phoque, PlusIcon, SearchIcon } from '@/shared/ui'
import { conversationListMessages } from '../i18n'

/** Ordinateur (dès 1024 px), tablette (dès 640 px) ou téléphone. */
export type ListVariant = 'large' | 'medium' | 'small'

interface ConversationListProps {
  /** Compte de la session : nomme une directe par son interlocuteur. */
  readonly me: string
  readonly selectedId: string | null
  readonly onNew: () => void
  readonly variant: ListVariant
  /** Titre « Conversations », qui reçoit le focus quand la conversation ouverte disparaît (WCAG 2.4.3). */
  readonly titleRef?: Ref<HTMLHeadingElement> | undefined
}

/** SEC-01 : saisie de recherche bornée, comme celle d'Amis (ADR 0038, point 6). */
const SEARCH_MAX_LENGTH = 64
// Cartes d'Etats, comme celles d'Amis (ADR 0038, point 9).
const STATE = 'flex flex-col items-center px-5.5 pt-8 pb-6 text-center'
const STATE_TITLE = 'm-0 mt-3 text-[18px] font-bold tracking-[-0.01em]'
const STATE_TEXT = 'm-0 mt-1.5 max-w-65 text-[14px] leading-[1.45] text-pretty text-ink-2'
const STATE_BUTTON =
  'mt-4 inline-flex h-11 items-center gap-2 rounded-[10px] pr-4 pl-3 text-[14.5px] font-semibold'

/**
 * CNV-04, CNV-10 : liste des conversations de Conv-Ordinateur, Conv-Tablette et Conv-Telephone-Liste. Chaque ligne
 * mène à `/?conversation={id}` (ADR 0041, point 1) ; la recherche porte sur les noms, côté client. Sans message avant
 * le lot 4, une ligne n'a ni aperçu, ni heure, ni non-lus (ADR 0041, point 7).
 */
export function ConversationList({
  me,
  selectedId,
  onNew,
  variant,
  titleRef,
}: ConversationListProps) {
  const t = useMessages(conversationListMessages)
  const conversations = useConversations()
  const [query, setQuery] = useState('')
  const searchRef = useRef<HTMLInputElement>(null)
  const searchLabel = variant === 'medium' ? t('searchShort') : t('search')
  // WCAG 4.1.2 : une directe sans interlocuteur n'a pas de nom ; le serveur l'exclut déjà (ADR 0039, point 6).
  const loaded = conversations.data?.filter(
    (conversation) => titleOf(conversation, me) !== undefined,
  )
  const shown = loaded?.filter((conversation) => matchConversation(conversation, me, query))

  return (
    <section aria-label={t('list')} className="flex min-h-0 flex-1 flex-col">
      <div className={`flex flex-col gap-3 pt-4 pb-3 ${variant === 'medium' ? 'px-3.5' : 'px-4'}`}>
        <div className="flex min-h-11 items-center justify-between gap-3">
          <h1
            ref={titleRef}
            tabIndex={-1}
            className={`m-0 font-bold tracking-[-0.025em] outline-none ${variant === 'small' ? 'text-[26px]' : 'text-[20px]'}`}
          >
            {t('title')}
          </h1>
          {variant === 'large' ? (
            <button
              type="button"
              aria-label={t('newConversation')}
              onClick={onNew}
              className="inline-flex h-9 items-center gap-1.5 rounded-lg bg-accent px-3 text-[13px] font-semibold text-on-accent hover:bg-accent-hover"
            >
              <PlusIcon size={16} />
              {t('newShort')}
            </button>
          ) : (
            <button
              type="button"
              aria-label={t('newConversation')}
              onClick={onNew}
              className="grid size-11 place-items-center rounded-full bg-accent text-on-accent hover:bg-accent-hover"
            >
              <PlusIcon size={20} />
            </button>
          )}
        </div>
        <label
          className={`flex items-center gap-2 rounded-[10px] bg-sunken px-3 text-ink-3 inset-ring inset-ring-line focus-within:ring-3 focus-within:ring-focus-glow focus-within:inset-ring-accent ${variant === 'large' ? 'h-10' : 'h-11'}`}
        >
          <SearchIcon className="flex-none" />
          <input
            ref={searchRef}
            type="search"
            maxLength={SEARCH_MAX_LENGTH}
            aria-label={searchLabel}
            placeholder={searchLabel}
            value={query}
            onChange={(event) => {
              setQuery(event.target.value)
            }}
            className="h-full min-w-0 flex-1 bg-transparent text-[15px] text-ink outline-none placeholder:text-ink-3"
          />
        </label>
      </div>
      {loaded === undefined ? (
        conversations.isError ? (
          <div className="px-4 py-6">
            <LoadErrorState
              heading="h2"
              onRetry={() => {
                void conversations.refetch()
              }}
            />
          </div>
        ) : (
          <ListSkeleton label={t('loading')} />
        )
      ) : loaded.length === 0 ? (
        <div className={STATE}>
          <Phoque variant="sleeping" size={96} />
          <h2 className={STATE_TITLE}>{t('emptyTitle')}</h2>
          <p className={STATE_TEXT}>{t('emptyText')}</p>
          <button
            type="button"
            onClick={onNew}
            className={`${STATE_BUTTON} bg-accent text-on-accent hover:bg-accent-hover`}
          >
            <PlusIcon />
            {t('newConversation')}
          </button>
        </div>
      ) : shown?.length === 0 ? (
        <div className={STATE}>
          <Phoque variant="searching" size={96} />
          <h2 className={STATE_TITLE}>{t('noMatchTitle')}</h2>
          <p className={`${STATE_TEXT} wrap-anywhere`}>{t('noMatch', { query: isolate(query) })}</p>
          <button
            type="button"
            onClick={() => {
              setQuery('')
              // WCAG 2.4.3 : le bouton disparaît avec l'état « Aucun résultat » ; le focus revient à la recherche.
              searchRef.current?.focus()
            }}
            className={`${STATE_BUTTON} bg-surface text-ink inset-ring inset-ring-line-strong`}
          >
            <CrossIcon size={18} strokeWidth={1.8} />
            {t('clearSearch')}
          </button>
        </div>
      ) : (
        <ul className="m-0 flex min-h-0 list-none flex-col overflow-y-auto px-2 pt-0.5 pb-4">
          {shown?.map((conversation) => (
            <Row
              key={conversation.id}
              conversation={conversation}
              me={me}
              selected={conversation.id === selectedId}
            />
          ))}
        </ul>
      )}
    </section>
  )
}

function Row({
  conversation,
  me,
  selected,
}: {
  readonly conversation: Conversation
  readonly me: string
  readonly selected: boolean
}) {
  return (
    <li>
      <Link
        to={conversationPath(conversation.id)}
        aria-current={selected ? 'page' : undefined}
        className={`flex items-center gap-3 rounded-[14px] px-2.5 py-3 text-ink no-underline ${
          selected ? 'bg-accent-soft' : 'hover:bg-sunken'
        }`}
      >
        <ConversationAvatar conversation={conversation} me={me} size={44} onAccent={selected} />
        <span className="min-w-0 flex-1 truncate text-[15px] font-semibold">
          {titleOf(conversation, me) ?? ''}
        </span>
      </Link>
    </li>
  )
}

/** Lignes du squelette d'Etats : largeur du nom, de l'aperçu et de l'heure. */
const SKELETON_ROWS = [
  { name: 'w-[52%]', preview: 'w-[84%]', time: 'w-8' },
  { name: 'w-[38%]', preview: 'w-[72%]', time: 'w-6.5' },
  { name: 'w-[60%]', preview: 'w-[66%]', time: 'w-7.5' },
] as const

/** Etats : squelette de la liste, sans scintillement quand les animations sont réduites. */
function ListSkeleton({ label }: { readonly label: string }) {
  const block = 'rounded-md bg-sunken motion-safe:animate-pulse'
  return (
    <div role="img" aria-label={label} className="flex flex-col gap-2 px-3 py-0.5">
      {SKELETON_ROWS.map((row) => (
        <div key={row.name} className="flex h-14 flex-none items-center gap-3 px-1.5">
          <span className="size-10 flex-none rounded-full bg-sunken motion-safe:animate-pulse" />
          <span className="flex flex-1 flex-col gap-2">
            <span className={`h-2.75 ${row.name} ${block}`} />
            <span className={`h-2.25 ${row.preview} ${block}`} />
          </span>
          <span className={`mt-3.75 h-2.25 flex-none self-start ${row.time} ${block}`} />
        </div>
      ))}
    </div>
  )
}
