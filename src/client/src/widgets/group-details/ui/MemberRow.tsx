import { initialsOf } from '@/entities/account'
import type { Member } from '@/entities/conversation'
import { tintOf } from '@/entities/friend'
import { useMessages } from '@/shared/i18n'
import { isolate } from '@/shared/lib'
import { Avatar } from '@/shared/ui'
import { groupDetailsMessages } from '../i18n'
import { PersonMinusIcon } from './icons'
import { Tag } from './parts'

/**
 * GRP-04 : un membre d'un groupe (Conv-Ordinateur, Conv-Tablette) : « Vous », « Admin », « Pas dans vos amis », et
 * « Retirer » pour l'administrateur. Empreinte et état de la clé attendent le lot 5 (ADR 0041, point 7).
 */
export function MemberRow({
  member,
  isMe,
  friend,
  onRemove,
}: {
  readonly member: Member
  readonly isMe: boolean
  /** Le membre est un ami de l'appelant, d'après sa liste d'amis lue. */
  readonly friend: boolean
  /** Absent : l'appelant ne peut pas le retirer (GRP-03). */
  readonly onRemove: (() => void) | undefined
}) {
  const t = useMessages(groupDetailsMessages)
  return (
    <li className="flex items-center gap-3 border-b border-line py-2.25">
      <Avatar initials={initialsOf(member.displayName)} tint={tintOf(member.userId)} size={36} />
      <div className="flex min-w-0 flex-1 flex-col gap-0.5">
        <span className="flex flex-wrap items-center gap-1.5">
          <span className="truncate text-[15px] font-semibold">
            {isMe ? t('you') : member.displayName}
          </span>
          {member.role === 'ADMIN' && <Tag accent>{t('admin')}</Tag>}
          {!isMe && !friend && <Tag>{t('notFriend')}</Tag>}
        </span>
        <span className="truncate font-mono text-[12px] text-ink-3">@{member.username}</span>
      </div>
      {onRemove && (
        <button
          type="button"
          aria-label={t('remove', { name: isolate(member.displayName) })}
          onClick={onRemove}
          className="grid size-9 flex-none place-items-center rounded-[8px] text-ink-2 hover:bg-sunken"
        >
          <PersonMinusIcon />
        </button>
      )}
    </li>
  )
}
