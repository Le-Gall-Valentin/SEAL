import {
  type Conversation,
  ConversationAvatar,
  counterpartOf,
  otherFirstNames,
  titleOf,
} from '@/entities/conversation'
import { useMessages } from '@/shared/i18n'
import { isolate } from '@/shared/lib'
import { Phoque } from '@/shared/ui'
import { conversationPanelMessages } from '../i18n'

interface ConversationPanelProps {
  readonly me: string
  /** La conversation ouverte ; `null` : aucune n'est choisie (ordinateur et tablette). */
  readonly conversation: Conversation | null
  readonly variant: 'large' | 'medium' | 'small'
  readonly infoOpen: boolean
  readonly onToggleInfo: () => void
  /** Téléphone : retour à la liste (Conv-Telephone-Fil). */
  readonly onBack: () => void
  readonly onNew: () => void
}

const NAMES = new Intl.ListFormat('fr', { style: 'long', type: 'conjunction' })

/**
 * CNV-04 : en-tête d'une conversation (Conv-Ordinateur, Conv-Telephone-Fil) et fil, vide jusqu'aux messages du lot 4,
 * sans composeur (ADR 0041, point 6). GRP-05 : l'annonce de l'arrivée dans un groupe. Sans conversation choisie,
 * l'état « Vos conversations sont scellées » d'Etats.
 */
export function ConversationPanel({
  me,
  conversation,
  variant,
  infoOpen,
  onToggleInfo,
  onBack,
  onNew,
}: ConversationPanelProps) {
  const t = useMessages(conversationPanelMessages)
  if (conversation === null) {
    return (
      <section className="flex flex-1 flex-col items-center justify-center px-6 text-center">
        <Phoque variant="neutral" size={96} />
        <h2 className="m-0 mt-3 text-[18px] font-bold tracking-[-0.01em]">{t('sealedTitle')}</h2>
        <p className="m-0 mt-1.5 max-w-72 text-[14px] leading-[1.45] text-pretty text-ink-2">
          {t('sealedText')}
        </p>
        <button
          type="button"
          onClick={onNew}
          className="mt-4 inline-flex h-11 items-center gap-2 rounded-[10px] bg-surface pr-4 pl-3 text-[14.5px] font-semibold text-ink inset-ring inset-ring-line-strong"
        >
          <PlusIcon />
          {t('newConversation')}
        </button>
      </section>
    )
  }
  const title = titleOf(conversation, me) ?? ''
  const group = conversation.type === 'GROUP'
  const subtitle = group
    ? // ADR 0017 : chaque prénom vient d'un autre compte et garde sa direction d'écriture.
      NAMES.format([...otherFirstNames(conversation, me).map(isolate), t('you')])
    : `@${counterpartOf(conversation, me)?.username ?? ''}`
  return (
    <section aria-label={title} className="flex min-h-0 flex-1 flex-col">
      <header className="flex min-h-16 items-center gap-3 border-b border-line bg-surface px-4 py-2.5">
        {variant === 'small' && (
          <button
            type="button"
            aria-label={t('back')}
            onClick={onBack}
            className="-ml-2 grid size-11 flex-none place-items-center rounded-[10px] text-ink-2"
          >
            <BackIcon />
          </button>
        )}
        <ConversationAvatar conversation={conversation} me={me} size={40} />
        <div className="flex min-w-0 flex-1 flex-col">
          <h2 className="m-0 truncate text-[16px] font-bold">{title}</h2>
          <span className={`truncate text-[12px] text-ink-3 ${group ? '' : 'font-mono'}`}>
            {subtitle}
          </span>
        </div>
        {group && (
          <button
            type="button"
            aria-label={t('info')}
            aria-expanded={infoOpen}
            onClick={onToggleInfo}
            className={`grid size-11 flex-none place-items-center rounded-[10px] ${infoOpen ? 'bg-accent-soft text-accent-text' : 'text-ink-2'}`}
          >
            <InfoIcon />
          </button>
        )}
      </header>
      <div className="flex min-h-0 flex-1 flex-col items-center justify-center gap-4 overflow-y-auto px-6 py-8 text-center">
        {group && (
          <p className="m-0 max-w-96 rounded-full bg-sunken px-3 py-1.5 text-[12.5px] text-ink-2">
            {t('arrival')}
          </p>
        )}
        <div className="flex flex-col items-center">
          <p className="m-0 text-[15px] font-semibold">{t('emptyTitle')}</p>
          <p className="m-0 mt-1 text-[13.5px] text-ink-3">{t('emptyText')}</p>
        </div>
      </div>
    </section>
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
      strokeWidth="2"
      strokeLinecap="round"
      aria-hidden="true"
    >
      <path d="M12 5v14M5 12h14" />
    </svg>
  )
}

function BackIcon() {
  return (
    <svg
      width="22"
      height="22"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="M15 5l-7 7 7 7" />
    </svg>
  )
}

function InfoIcon() {
  return (
    <svg
      width="22"
      height="22"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.7"
      strokeLinecap="round"
      aria-hidden="true"
    >
      <circle cx="12" cy="12" r="9" />
      <path d="M12 11v5M12 8h.01" />
    </svg>
  )
}
