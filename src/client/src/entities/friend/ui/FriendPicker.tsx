import { type KeyboardEvent, useId, useState } from 'react'
import { initialsOf } from '@/entities/account/@x/friend'
import { useMessages } from '@/shared/i18n'
import { isolate, useClock } from '@/shared/lib'
import { Avatar } from '@/shared/ui'
import { friendMessages } from '../i18n'
import { tintOf } from '../model/friends'
import { type Friend, type MatchRange, rankFriends } from '../model/friendPicker'

interface FriendPickerProps {
  /** Intitulé visible de la zone, qui nomme aussi la saisie (« Membres »). */
  readonly label: string
  readonly friends: readonly Friend[]
  readonly selected: readonly Friend[]
  /** Comptes déjà membres, jamais proposés (10.3, étape 2). */
  readonly exclude: ReadonlySet<string>
  /** Taille maximale de la sélection : 1 pour une directe, les places restantes d'un groupe sinon (GRP-01). */
  readonly max: number
  readonly onSelect: (friend: Friend) => void
  readonly onRemove: (friend: Friend) => void
}

/** Saisie de 64 caractères au plus, comme la recherche d'Amis : un identifiant ou un nom affiché tient en dessous. */
const QUERY_MAX_LENGTH = 64

/**
 * FRD-20, maquette Nouvelle-Conversation : sélecteur d'amis au clavier (↑ ↓, Entrée, Échap), calculé sur la liste
 * d'amis chargée ; la saisie ne part jamais au serveur (10.3). Échap vide la saisie, puis ferme la liste, et ne laisse
 * le dialogue parent se fermer qu'au troisième appui (ADR 0041).
 */
export function FriendPicker({
  label,
  friends,
  selected,
  exclude,
  max,
  onSelect,
  onRemove,
}: FriendPickerProps) {
  const t = useMessages(friendMessages)
  const clock = useClock()
  const labelId = useId()
  const listId = useId()
  const [query, setQuery] = useState('')
  const [open, setOpen] = useState(true)
  const [active, setActive] = useState(0)

  const full = selected.length >= max
  const excluded = new Set([...exclude, ...selected.map((friend) => friend.userId)])
  const ranked = rankFriends(friends, query, excluded, new Date(clock.now()))
  const activeIndex = Math.min(active, Math.max(0, ranked.length - 1))
  const showList = open && !full && ranked.length > 0
  const optionId = (index: number) => `${listId}-${String(index)}`

  function choose(friend: Friend) {
    onSelect(friend)
    setQuery('')
    setActive(0)
  }

  function onKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    switch (event.key) {
      case 'ArrowDown':
      case 'ArrowUp': {
        event.preventDefault()
        const step = event.key === 'ArrowDown' ? 1 : -1
        setOpen(true)
        setActive(Math.min(Math.max(activeIndex + step, 0), Math.max(0, ranked.length - 1)))
        return
      }
      case 'Enter': {
        event.preventDefault()
        const chosen = showList ? ranked[activeIndex] : undefined
        if (chosen) {
          choose(chosen.friend)
        }
        return
      }
      case 'Escape':
        if (query !== '' || open) {
          // Le dialogue parent se ferme sur l'annulation que déclenche Échap : on la retient tant qu'il reste à effacer.
          event.preventDefault()
          event.stopPropagation()
          if (query === '') {
            setOpen(false)
          } else {
            setQuery('')
            setActive(0)
          }
        }
        return
      default:
    }
  }

  return (
    <div className="flex flex-col gap-2">
      <span id={labelId} className="text-[12px] font-bold tracking-[0.08em] text-ink-3 uppercase">
        {label}
      </span>
      <div className="flex min-h-11 flex-wrap items-center gap-1.5 rounded-[10px] bg-surface px-2 py-1.5 inset-ring inset-ring-line-strong focus-within:ring-3 focus-within:ring-focus-glow focus-within:inset-ring-accent">
        {selected.map((friend) => (
          <span
            key={friend.userId}
            className="inline-flex h-8 items-center gap-1.5 rounded-full bg-accent-soft pr-0.5 pl-1 text-[13px] font-medium"
          >
            <Avatar
              initials={initialsOf(friend.displayName)}
              tint={tintOf(friend.userId)}
              size={24}
            />
            {friend.displayName}
            <button
              type="button"
              aria-label={t('pickerRemove', { name: isolate(friend.displayName) })}
              onClick={() => {
                onRemove(friend)
              }}
              className="grid size-7 place-items-center rounded-full text-ink-2 hover:bg-accent-soft-2"
            >
              <CrossIcon />
            </button>
          </span>
        ))}
        <span className="inline-flex h-8 min-w-25 flex-1 items-center gap-1.5">
          <SearchIcon />
          <input
            role="combobox"
            aria-labelledby={labelId}
            aria-expanded={showList}
            aria-controls={listId}
            aria-autocomplete="list"
            aria-activedescendant={showList ? optionId(activeIndex) : undefined}
            autoComplete="off"
            spellCheck={false}
            maxLength={QUERY_MAX_LENGTH}
            disabled={full}
            value={query}
            onChange={(event) => {
              setQuery(event.target.value)
              setOpen(true)
              setActive(0)
            }}
            onKeyDown={onKeyDown}
            className="h-full min-w-0 flex-1 bg-transparent text-[15px] text-ink outline-none disabled:opacity-55"
          />
        </span>
      </div>
      <div className="flex flex-col gap-0.5 text-[12px] text-ink-3">
        <span>{t('pickerHint')}</span>
        <span>{t('pickerKeys')}</span>
      </div>
      {friends.length === 0 ? (
        <p className="text-[13px] text-ink-3">{t('pickerNoFriends')}</p>
      ) : showList ? (
        <>
          <span className="px-2 pt-1.5 pb-0.5 text-[11px] font-bold tracking-[0.08em] text-ink-3 uppercase">
            {t('pickerBest')}
          </span>
          <ul
            id={listId}
            role="listbox"
            aria-label={t('pickerList')}
            className="flex flex-col gap-0.5"
          >
            {ranked.map(({ friend, usernameMatch, nameMatch }, index) => (
              <li
                key={friend.userId}
                id={optionId(index)}
                role="option"
                aria-selected={index === activeIndex}
                onMouseDown={(event) => {
                  // Combobox WAI-ARIA : le clavier passe par la saisie (aria-activedescendant) ; à la souris, l'option
                  // se choisit à l'appui, sans prendre le focus, pour que la navigation au clavier continue.
                  event.preventDefault()
                  choose(friend)
                }}
                className={`flex min-h-13 cursor-pointer items-center gap-3 rounded-[10px] px-2 py-1.5 ${
                  index === activeIndex
                    ? 'bg-accent-soft inset-ring inset-ring-accent/25'
                    : 'hover:bg-sunken'
                }`}
              >
                <Avatar
                  initials={initialsOf(friend.displayName)}
                  tint={tintOf(friend.userId)}
                  size={36}
                />
                <span className="flex min-w-0 flex-1 flex-col">
                  <span className="truncate font-semibold">
                    <Highlighted text={friend.displayName} range={nameMatch} />
                  </span>
                  <span className="truncate font-mono text-[12px] text-ink-3">
                    @<Highlighted text={friend.username} range={usernameMatch} />
                  </span>
                </span>
                {index === activeIndex ? <PlusIcon /> : null}
              </li>
            ))}
          </ul>
        </>
      ) : open && !full && query !== '' ? (
        <p role="status" className="text-[13px] text-ink-3">
          {t('pickerNoMatch', { query: isolate(query) })}
        </p>
      ) : null}
    </div>
  )
}

/** 10.3, étape 4 : la partie qui correspond à la saisie, surlignée dans le texte affiché. */
function Highlighted({
  text,
  range,
}: {
  readonly text: string
  readonly range: MatchRange | null
}) {
  if (range === null) {
    return text
  }
  const [start, end] = range
  return (
    <>
      {text.slice(0, start)}
      <mark className="rounded-[3px] bg-accent-soft-2 text-inherit">{text.slice(start, end)}</mark>
      {text.slice(end)}
    </>
  )
}

function SearchIcon() {
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
      className="flex-none text-ink-3"
    >
      <circle cx="11" cy="11" r="6.5" />
      <path d="M20 20l-4.3-4.3" />
    </svg>
  )
}

function CrossIcon() {
  return (
    <svg
      width="14"
      height="14"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      aria-hidden="true"
    >
      <path d="M6.5 6.5l11 11M17.5 6.5l-11 11" />
    </svg>
  )
}

function PlusIcon() {
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
      className="flex-none text-ink-3"
    >
      <path d="M12 5v14M5 12h14" />
    </svg>
  )
}
