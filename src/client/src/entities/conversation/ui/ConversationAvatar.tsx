import { initialsOf } from '@/entities/account/@x/conversation'
import { tintOf } from '@/entities/friend/@x/conversation'
import { Avatar, TINT_CLASS } from '@/shared/ui'
import type { Conversation } from '../model/conversations'
import { counterpartOf } from '../model/conversations'

type Size = 40 | 44 | 72

interface ConversationAvatarProps {
  readonly conversation: Conversation
  readonly me: string
  readonly size: Size
  /** Joints de la mosaïque à la couleur d'une ligne sélectionnée (--accent-soft) au lieu de --surface. */
  readonly onAccent?: boolean
}

// Maquette Conv-Ordinateur : mosaïque de 40 (en-tête), 44 (liste) et 72 px (infos du groupe).
const MOSAIC: Record<Size, { readonly box: string; readonly big: string; readonly small: string }> =
  {
    40: { box: 'size-10 gap-[1.5px]', big: 'pl-0.75 text-[14px]', small: 'text-[10px]' },
    44: { box: 'size-11 gap-[1.5px]', big: 'pl-1 text-[15px]', small: 'text-[11px]' },
    72: { box: 'size-18 gap-0.5', big: 'pl-1.25 text-[24px]', small: 'text-[17px]' },
  }

/** Première lettre d'un nom, en capitale ; les initiales seulement, jamais de photo (CDC, section 2). */
function letterOf(name: string): string {
  return (Array.from(name)[0] ?? '').toLocaleUpperCase('fr')
}

/**
 * CNV-04 : avatar d'une conversation, décoratif (son nom est lu à côté). Une directe prend les initiales de
 * l'interlocuteur ; un groupe, la mosaïque des trois premiers autres membres dans l'ordre d'arrivée, une lettre et une
 * teinte chacun, réduite à deux ou une case s'ils sont moins nombreux, et à l'initiale du groupe s'il est seul.
 */
export function ConversationAvatar({
  conversation,
  me,
  size,
  onAccent = false,
}: ConversationAvatarProps) {
  if (conversation.type === 'DIRECT') {
    const counterpart = counterpartOf(conversation, me)
    return (
      <Avatar
        initials={counterpart ? initialsOf(counterpart.displayName) : ''}
        tint={counterpart ? tintOf(counterpart.userId) : undefined}
        size={size === 72 ? 44 : size}
      />
    )
  }
  const others = conversation.members.filter((member) => member.userId !== me).slice(0, 3)
  const cells =
    others.length > 0
      ? others.map((member) => ({
          key: member.userId,
          letter: letterOf(member.displayName),
          tint: tintOf(member.userId),
        }))
      : [{ key: conversation.id, letter: letterOf(conversation.name ?? ''), tint: 'moss' as const }]
  const sizes = MOSAIC[size]
  const columns = cells.length === 1 ? 'grid-cols-1' : 'grid-cols-2'
  return (
    <span
      aria-hidden="true"
      className={`grid flex-none grid-rows-2 overflow-hidden rounded-full leading-none font-semibold text-ink-2 ${columns} ${sizes.box} ${onAccent ? 'bg-accent-soft' : 'bg-surface'}`}
    >
      {cells.map((cell, index) => (
        <span
          key={cell.key}
          className={`grid place-items-center ${TINT_CLASS[cell.tint]} ${
            index === 0 || cells.length === 2 ? `row-span-2 ${sizes.big}` : sizes.small
          }`}
        >
          {cell.letter}
        </span>
      ))}
    </span>
  )
}
