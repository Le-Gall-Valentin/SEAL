import { useId } from 'react'
import type { ReceivedRequest, SentRequest } from '@/entities/friend'
import { ReceivedRequestRow, SentRequestRow } from '@/features/answer-friend-request'
import { useMessages } from '@/shared/i18n'
import { friendsPageMessages } from '../i18n'

interface InvitationsPanelProps {
  readonly received: readonly ReceivedRequest[]
  readonly sent: readonly SentRequest[]
  readonly busyId: string | null
  readonly onAccept: (requestId: string) => void
  readonly onDecline: (requestId: string) => void
  readonly onRevoke: (requestId: string) => void
}

const CARD = 'm-0 list-none rounded-[18px] bg-surface p-0 inset-ring inset-ring-line'
const EMPTY =
  'm-0 rounded-[18px] bg-surface px-4 py-3.5 text-[14px] text-ink-3 inset-ring inset-ring-line'
const TITLE =
  'm-0 mb-2 flex items-center gap-2 text-[12px] font-bold tracking-[0.08em] text-ink-3 uppercase'

/** Onglet Invitations d'Amis-Tablette : « Reçues » puis « Envoyées », la plus récente d'abord. */
export function InvitationsPanel({
  received,
  sent,
  busyId,
  onAccept,
  onDecline,
  onRevoke,
}: InvitationsPanelProps) {
  const t = useMessages(friendsPageMessages)
  const receivedId = useId()
  const sentId = useId()
  return (
    <div className="mt-1 flex flex-col gap-6">
      <section aria-labelledby={receivedId}>
        <h2 id={receivedId} className={TITLE}>
          {t('received')}
          {received.length > 0 && (
            <span className="grid h-5 min-w-5 place-items-center rounded-full bg-accent px-1.5 text-[11px] font-bold tracking-normal text-on-accent">
              {received.length}
            </span>
          )}
        </h2>
        {received.length === 0 ? (
          <p className={EMPTY}>{t('noReceived')}</p>
        ) : (
          <ul className={CARD}>
            {received.map((request) => (
              <ReceivedRequestRow
                key={request.id}
                request={request}
                busy={busyId !== null}
                onAccept={onAccept}
                onDecline={onDecline}
              />
            ))}
          </ul>
        )}
      </section>
      <section aria-labelledby={sentId}>
        <h2 id={sentId} className={TITLE}>
          {t('sent')}
        </h2>
        {sent.length === 0 ? (
          <p className={EMPTY}>{t('noSent')}</p>
        ) : (
          <ul className={CARD}>
            {sent.map((request) => (
              <SentRequestRow
                key={request.id}
                request={request}
                busy={busyId !== null}
                onRevoke={onRevoke}
              />
            ))}
          </ul>
        )}
      </section>
    </div>
  )
}
