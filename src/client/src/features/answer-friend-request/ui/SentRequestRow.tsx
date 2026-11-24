import { RequestTime, type SentRequest, tintOf, usernameInitials } from '@/entities/friend'
import { useMessages } from '@/shared/i18n'
import { answerFriendRequestMessages } from '../i18n'
import { ClockIcon } from './icons'
import { RequestRow } from './RequestRow'

interface SentRequestRowProps {
  readonly request: SentRequest
  readonly busy: boolean
  readonly onRevoke: (requestId: string) => void
}

/**
 * Invitation envoyée (Amis-Tablette) : le destinataire par son identifiant seul (API-01, ADR 0038, point 7), la date,
 * « En attente » et « Révoquer ».
 */
export function SentRequestRow({ request, busy, onRevoke }: SentRequestRowProps) {
  const t = useMessages(answerFriendRequestMessages)
  const username = `@${request.username}`
  return (
    <RequestRow
      initials={usernameInitials(request.username)}
      tint={tintOf(request.userId)}
      title={<span className="truncate font-mono text-[14px] font-semibold">{username}</span>}
      details={<RequestTime variant="sent" date={request.createdAt} />}
      actions={
        <>
          <span className="inline-flex h-5.5 items-center gap-1 rounded-full px-2 text-[12px] font-semibold text-ink-2 inset-ring inset-ring-line-strong">
            <ClockIcon />
            {t('pending')}
          </span>
          <button
            type="button"
            disabled={busy}
            aria-label={t('revokeLabel', { username })}
            onClick={() => {
              onRevoke(request.id)
            }}
            className="min-h-11 rounded-lg px-3 text-[14px] font-semibold text-ink-2 hover:bg-sunken disabled:opacity-60"
          >
            {t('revoke')}
          </button>
        </>
      }
    />
  )
}
