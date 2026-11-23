import { useState } from 'react'
import { initialsOf } from '@/entities/account'
import { type Friend, matchFriend, tintOf } from '@/entities/friend'
import { PersonPlusIcon } from '@/features/send-friend-request'
import { useMessages } from '@/shared/i18n'
import { Avatar, Phoque } from '@/shared/ui'
import { friendsPageMessages } from '../i18n'
import { FriendMenu } from './FriendMenu'
import { SearchIcon } from './icons'

interface FriendListProps {
  readonly friends: readonly Friend[]
  readonly onAdd: () => void
  readonly onRemove: (friend: Friend) => void
}

const CARD = 'rounded-[18px] bg-surface inset-ring inset-ring-line'

/** FRD-10 : carte de la liste d'Amis-Ordinateur, recherche locale (ADR 0038, point 6), état « compte neuf » d'Etats. */
export function FriendList({ friends, onAdd, onRemove }: FriendListProps) {
  const t = useMessages(friendsPageMessages)
  const [query, setQuery] = useState('')
  if (friends.length === 0) {
    return (
      <section className={`flex flex-col items-center gap-3 px-6 py-10 text-center ${CARD}`}>
        <Phoque variant="waving" size={96} />
        <h2 className="m-0 text-[18px] font-bold">{t('emptyTitle')}</h2>
        <p className="m-0 max-w-90 text-[14px] leading-[1.45] text-ink-2">{t('emptyText')}</p>
        <button
          type="button"
          onClick={onAdd}
          className="inline-flex h-11 items-center gap-2 rounded-[10px] bg-accent pr-4 pl-3.5 text-[14.5px] font-semibold text-on-accent hover:bg-accent-hover"
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
        <div className="flex flex-col items-center gap-2 border-t border-line px-4 py-6 text-center">
          <p className="m-0 text-[14px] text-ink-2">{t('noMatch', { query })}</p>
          <button
            type="button"
            onClick={() => {
              setQuery('')
            }}
            className="min-h-11 rounded-lg px-3 text-[14px] font-semibold text-accent-text underline underline-offset-[0.2em]"
          >
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
