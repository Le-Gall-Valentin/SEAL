import { type Conversation, ConversationAvatar } from '@/entities/conversation'
import { useMessages } from '@/shared/i18n'
import { groupDetailsMessages } from '../i18n'
import { PencilIcon } from './icons'
import { LinkButton } from './parts'

const DAY_MONTH = new Intl.DateTimeFormat('fr', { day: 'numeric', month: 'short' })

/**
 * En-tête des infos d'un groupe (Conv-Ordinateur) : mosaïque, nom, « {n} membres · Créé le {date} » (ADR 0041,
 * point 5) et, pour l'administrateur, « Renommer le groupe ».
 */
export function GroupSummary({
  me,
  conversation,
  onRename,
}: {
  readonly me: string
  readonly conversation: Conversation
  /** Absent : l'appelant ne peut pas renommer (GRP-03). */
  readonly onRename: (() => void) | undefined
}) {
  const t = useMessages(groupDetailsMessages)
  const count = conversation.members.length
  const date = DAY_MONTH.format(conversation.createdAt)
  return (
    <div className="flex flex-col items-center gap-1.5 text-center">
      <ConversationAvatar conversation={conversation} me={me} size={72} />
      {/* ACC-03 : des marques combinantes empilées restent dans le titre, que le rembourrage laisse respirer. */}
      <h3 className="m-0 -mb-1 overflow-hidden py-1 text-[20px] font-bold tracking-[-0.015em] wrap-anywhere">
        {conversation.name}
      </h3>
      <p className="m-0 text-[13px] text-ink-3">
        {count === 1 ? t('metaOne', { date }) : t('meta', { count: String(count), date })}
      </p>
      {onRename && (
        <LinkButton onClick={onRename}>
          <PencilIcon />
          {t('rename')}
        </LinkButton>
      )}
    </div>
  )
}
