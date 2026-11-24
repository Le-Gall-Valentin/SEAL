import { useState } from 'react'
import { initialsOf } from '@/entities/account'
import { type Friend, matchFriend, tintOf } from '@/entities/friend'
import { PersonPlusIcon } from '@/features/send-friend-request'
import { useMessages } from '@/shared/i18n'
import { Avatar, Phoque } from '@/shared/ui'
import { friendsPageMessages } from '../i18n'
import { FriendMenu } from './FriendMenu'
import { ClearIcon, SearchIcon } from './icons'

interface FriendListProps {
  readonly friends: readonly Friend[]
  readonly onAdd: () => void
  readonly onRemove: (friend: Friend) => void
}

const CARD = 'rounded-[18px] bg-surface inset-ring inset-ring-line'
/** SEC-01 : saisie de recherche bornée, comme celle d'un identifiant à inviter (ADR 0037, point 6). */
const SEARCH_MAX_LENGTH = 64
// ADR 0038 (point 9) : cartes d'Etats, « Amis · compte neuf » et « Conversations · recherche ».
const STATE = 'flex flex-col items-center px-5.5 pt-5.5 pb-6 text-center'
const STATE_TITLE = 'm-0 mt-3 text-[18px] font-bold tracking-[-0.01em]'
const STATE_TEXT =
  'm-0 mt-1.5 min-h-[41px] max-w-65 text-[14px] leading-[1.45] text-pretty text-ink-2'
const STATE_BUTTON =
  'mt-4 inline-flex h-11 items-center gap-2 rounded-[10px] pr-4 pl-3 text-[14.5px] font-semibold'

/** FRD-10 : carte de la liste d'Amis-Ordinateur, recherche locale (ADR 0038, point 6), état « compte neuf » d'Etats. */
export function FriendList({ friends, onAdd, onRemove }: FriendListProps) {
  const t = useMessages(friendsPageMessages)
  const [query, setQuery] = useState('')
  if (friends.length === 0) {
    return (
      <section className={`${STATE} ${CARD}`}>
        <Phoque variant="waving" size={96} />
        <h2 className={STATE_TITLE}>{t('emptyTitle')}</h2>
        <p className={STATE_TEXT}>{t('emptyText')}</p>
        <button
          type="button"
          onClick={onAdd}
          className={`${STATE_BUTTON} bg-accent text-on-accent hover:bg-accent-hover`}
        >
          <PersonPlusIcon />
          {t('addFriend')}
        </button>
      </section>
    )
  }
  const shown = friends.filter((friend) => matchFriend(friend, query))
  return (
    <div className={CARD}>
      <div className="flex items-center gap-3 p-3">
        <label className="flex h-11 min-w-0 flex-1 items-center gap-2 rounded-[10px] px-3 text-ink-3 inset-ring inset-ring-line-strong focus-within:ring-3 focus-within:ring-focus-glow focus-within:inset-ring-accent">
          <SearchIcon />
          <input
            type="search"
            maxLength={SEARCH_MAX_LENGTH}
            aria-label={t('search')}
            placeholder={t('search')}
            value={query}
            onChange={(event) => {
              setQuery(event.target.value)
            }}
            className="h-full min-w-0 flex-1 bg-transparent text-[15px] text-ink outline-none placeholder:text-ink-3"
          />
        </label>
        <span className="text-[13px] whitespace-nowrap text-ink-3">
          {friends.length === 1
            ? t('oneFriend')
            : t('friendCount', { count: String(friends.length) })}
        </span>
      </div>
      {shown.length === 0 ? (
        <div className={`${STATE} border-t border-line`}>
          <Phoque variant="searching" size={96} />
          <h2 className={STATE_TITLE}>{t('noMatchTitle')}</h2>
          <p className={`${STATE_TEXT} wrap-anywhere`}>{t('noMatch', { query })}</p>
          <button
            type="button"
            onClick={() => {
              setQuery('')
            }}
            className={`${STATE_BUTTON} bg-surface text-ink inset-ring inset-ring-line-strong`}
          >
            <ClearIcon />
            {t('clearSearch')}
          </button>
        </div>
      ) : (
        <ul className="m-0 list-none border-t border-line p-0">
          {shown.map((friend) => (
            <li
              key={friend.userId}
              className="flex items-center gap-3 border-b border-line py-3 pr-2 pl-4 last:border-b-0 sm:pr-3"
            >
              <Avatar
                initials={initialsOf(friend.displayName)}
                tint={tintOf(friend.userId)}
                size={44}
              />
              <div className="flex min-w-0 flex-1 flex-col">
                <span className="truncate font-semibold">{friend.displayName}</span>
                <span className="truncate font-mono text-[12px] text-ink-3">
                  @{friend.username}
                </span>
              </div>
              <FriendMenu friend={friend} onRemove={onRemove} />
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
