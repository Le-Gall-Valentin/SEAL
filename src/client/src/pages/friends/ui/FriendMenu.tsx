import { useEffect, useId, useRef, useState } from 'react'
import type { Friend } from '@/entities/friend'
import { useMessages } from '@/shared/i18n'
import { friendsPageMessages } from '../i18n'
import { MoreIcon, PersonMinusIcon } from './icons'

interface FriendMenuProps {
  readonly friend: Friend
  readonly onRemove: (friend: Friend) => void
}

/**
 * Menu « Plus d’actions » d'une ligne (Amis-Ordinateur). Seul « Retirer des amis » existe avant les lots des autres
 * actions (ADR 0038, point 3). Clavier : le menu prend le focus, Échap le ferme et le rend au bouton.
 */
export function FriendMenu({ friend, onRemove }: FriendMenuProps) {
  const t = useMessages(friendsPageMessages)
  const [open, setOpen] = useState(false)
  const menuId = useId()
  const container = useRef<HTMLDivElement>(null)
  const button = useRef<HTMLButtonElement>(null)
  const item = useRef<HTMLButtonElement>(null)
  useEffect(() => {
    if (!open) {
      return
    }
    item.current?.focus()
    const closeOutside = (event: MouseEvent) => {
      if (event.target instanceof Node && !container.current?.contains(event.target)) {
        setOpen(false)
      }
    }
    document.addEventListener('mousedown', closeOutside)
    return () => {
      document.removeEventListener('mousedown', closeOutside)
    }
  }, [open])
  const close = () => {
    setOpen(false)
    button.current?.focus()
  }
  const name = friend.displayName
  return (
    <div ref={container} className="relative">
      <button
        ref={button}
        type="button"
        aria-label={t('moreActions', { name })}
        aria-haspopup="menu"
        aria-expanded={open}
        aria-controls={open ? menuId : undefined}
        onClick={() => {
          setOpen((wasOpen) => !wasOpen)
        }}
        className={`grid size-11 place-items-center rounded-[10px] ${open ? 'bg-accent-soft text-accent-text' : 'text-ink-2 hover:bg-sunken'}`}
      >
        <MoreIcon />
      </button>
      {open && (
        <div
          id={menuId}
          role="menu"
          tabIndex={-1}
          aria-label={t('actions', { name })}
          onKeyDown={(event) => {
            if (event.key === 'Escape') {
              event.preventDefault()
              close()
            } else if (event.key === 'Tab') {
              setOpen(false)
            } else if (['ArrowDown', 'ArrowUp', 'Home', 'End'].includes(event.key)) {
              event.preventDefault()
              item.current?.focus()
            }
          }}
          className="absolute top-12 right-0 z-10 w-62 rounded-[14px] bg-raised p-1.5 shadow-dialog inset-ring inset-ring-line"
        >
          <button
            ref={item}
            type="button"
            role="menuitem"
            onClick={() => {
              setOpen(false)
              onRemove(friend)
            }}
            className="flex min-h-10 w-full items-center gap-2.5 rounded-lg px-3 text-left text-[15px] text-ink hover:bg-sunken focus:bg-sunken focus:outline-none"
          >
            <PersonMinusIcon />
            {t('remove')}
          </button>
        </div>
      )}
    </div>
  )
}
