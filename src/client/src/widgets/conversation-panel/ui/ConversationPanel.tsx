import type { Ref } from 'react'
import {
  type Conversation,
  ConversationAvatar,
  counterpartOf,
  otherFirstNames,
  titleOf,
} from '@/entities/conversation'
import { useMessages } from '@/shared/i18n'
import { isolate } from '@/shared/lib'
import { Phoque, PlusIcon } from '@/shared/ui'
import { conversationPanelMessages } from '../i18n'

interface ConversationPanelProps {
  readonly me: string
  /** La conversation ouverte ; `null` : aucune n'est choisie (ordinateur et tablette). */
  readonly conversation: Conversation | null
  readonly variant: 'large' | 'medium' | 'small'
  readonly infoOpen: boolean
  readonly onToggleInfo: () => void
  /** Bouton « Infos du groupe », qui reprend le focus quand l'aside se ferme (WCAG 2.4.3). */
  readonly infoRef?: Ref<HTMLButtonElement> | undefined
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
  infoRef,
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
      <header
        className={`flex min-h-16 items-center border-b border-line bg-surface py-2 ${HEADER[variant]}`}
      >
        {variant === 'small' && (
          <button
            type="button"
            aria-label={t('back')}
            onClick={onBack}
            className="grid size-11 flex-none place-items-center rounded-[10px] text-ink-2"
          >
            <BackIcon />
          </button>
        )}
        <ConversationAvatar conversation={conversation} me={me} size={40} />
        <div
          className={`flex min-w-0 flex-1 flex-col gap-px ${variant === 'small' ? 'px-1' : 'px-1.5 py-1'}`}
        >
          <h2 className="m-0 truncate text-[16px] font-bold">{title}</h2>
          <span className={`truncate text-[12px] text-ink-3 ${group ? '' : 'font-mono'}`}>
            {subtitle}
          </span>
        </div>
        {group && (
          <button
            type="button"
            ref={infoRef}
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

/** Marges de l'en-tête : Conv-Ordinateur, Conv-Tablette, Conv-Telephone-Fil. */
const HEADER: Record<ConversationPanelProps['variant'], string> = {
  large: 'gap-2.5 pr-3 pl-4',
  medium: 'gap-2.5 pr-2 pl-3.5',
  small: 'gap-1.5 pr-2.5 pl-1',
}

/** Maquette Etats : squelette du fil, sans scintillement quand les animations sont réduites. */
export function ConversationPanelSkeleton() {
  const t = useMessages(conversationPanelMessages)
  const block = 'bg-sunken motion-safe:animate-pulse'
  return (
    <div role="img" aria-label={t('loading')} className="flex min-h-0 flex-1 flex-col bg-surface">
      <div className="flex h-14 flex-none items-center gap-3 border-b border-line px-4">
        <span className={`size-8.5 flex-none rounded-full ${block}`} />
        <span className="flex flex-col gap-1.75">
          <span className={`h-2.75 w-35 rounded-md ${block}`} />
          <span className={`h-2.25 w-22.5 rounded-md ${block}`} />
        </span>
        <span className="flex-1" />
        <span className={`h-6.5 w-23 rounded-full ${block}`} />
      </div>
      <div className="flex min-h-0 flex-1 flex-col gap-2.5 px-5 py-4">
        <span className={`h-9.5 w-[46%] self-start rounded-[4px_16px_16px_16px] ${block}`} />
        <span className="h-9.5 w-[34%] self-end rounded-[16px_4px_16px_16px] bg-accent-soft motion-safe:animate-pulse" />
        <span className={`h-13 w-[58%] self-start rounded-[4px_16px_16px_16px] ${block}`} />
      </div>
    </div>
  )
}

function BackIcon() {
  return (
    <svg
      width="20"
      height="20"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.7"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="M14.5 5.5L8 12l6.5 6.5" />
    </svg>
  )
}

function InfoIcon() {
  return (
    <svg
      width="20"
      height="20"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.7"
      strokeLinecap="round"
      aria-hidden="true"
    >
      <circle cx="12" cy="12" r="8.5" />
      <path d="M12 11v5.5M12 7.8h.01" />
    </svg>
  )
}
