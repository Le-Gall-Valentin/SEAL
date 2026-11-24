import { initialsOf } from '@/entities/account'
import { type ReceivedRequest, RequestTime, tintOf } from '@/entities/friend'
import { useMessages } from '@/shared/i18n'
import { answerFriendRequestMessages } from '../i18n'
import { CheckIcon } from './icons'
import { RequestRow } from './RequestRow'

interface ReceivedRequestRowProps {
  readonly request: ReceivedRequest
  readonly busy: boolean
  readonly onAccept: (requestId: string) => void
  readonly onDecline: (requestId: string) => void
}

/** Invitation reçue (Amis-Tablette) : l'émetteur, son identifiant et la date, « Refuser » et « Accepter ». */
export function ReceivedRequestRow({
  request,
  busy,
  onAccept,
  onDecline,
}: ReceivedRequestRowProps) {
  const t = useMessages(answerFriendRequestMessages)
  const name = request.displayName
  return (
    <RequestRow
      initials={initialsOf(name)}
      tint={tintOf(request.userId)}
      title={<span className="truncate font-semibold">{name}</span>}
      details={
        <>
          <span className="truncate font-mono text-[12px] text-ink-3">@{request.username}</span>
          <RequestTime variant="received" date={request.createdAt} />
        </>
      }
      actions={
        <>
          <button
            type="button"
            disabled={busy}
            aria-label={t('declineLabel', { name })}
            onClick={() => {
              onDecline(request.id)
            }}
            className="min-h-11 rounded-lg bg-surface px-3.5 text-[14px] font-semibold text-ink inset-ring inset-ring-line-strong disabled:opacity-60"
          >
            {t('decline')}
          </button>
          <button
            type="button"
            disabled={busy}
            aria-label={t('acceptLabel', { name })}
            onClick={() => {
              onAccept(request.id)
            }}
            className="inline-flex min-h-11 items-center gap-1.5 rounded-lg bg-accent pr-3.5 pl-3 text-[14px] font-semibold text-on-accent hover:bg-accent-hover disabled:opacity-60"
          >
            <CheckIcon />
            {t('accept')}
          </button>
        </>
      }
    />
  )
}
