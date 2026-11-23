import { useId } from 'react'
import { useMessages } from '@/shared/i18n'
import { Alert, FieldError, FieldLabel, FormAlert, Notice, PrimaryButton } from '@/shared/ui'
import { sendFriendRequestMessages } from '../i18n'
import { INVITED_USERNAME_MAX_LENGTH } from '../model/invitedUsername'
import { type SendOutcome, useSendFriendRequest } from '../model/useSendFriendRequest'
import { UsernameInText } from './UsernameInText'

interface AddFriendFormProps {
  /** FRD-01 : « Accepter l’invitation » après un 409 INCOMING_REQUEST_EXISTS ; la page fournit l'acceptation. */
  readonly onAccept: (requestId: string) => void
  readonly accepting: boolean
}

/** Formulaire « Ajouter un ami » de l'aside (Amis-Ordinateur) et de la feuille (Amis-Telephone), ADR 0038. */
export function AddFriendForm({ onAccept, accepting }: AddFriendFormProps) {
  const t = useMessages(sendFriendRequestMessages)
  const form = useSendFriendRequest()
  const id = useId()
  const errorId = `${id}-erreur`
  return (
    <form
      noValidate
      className="flex flex-col gap-3.5"
      onSubmit={(event) => {
        event.preventDefault()
        form.submit()
      }}
    >
      <div className="flex flex-wrap items-end gap-2">
        <div className="flex min-w-0 flex-[1_1_192px] flex-col gap-1.5">
          <FieldLabel htmlFor={id}>{t('username')}</FieldLabel>
          <div className="flex h-11 items-center rounded-[10px] bg-surface px-3 inset-ring inset-ring-line-strong focus-within:ring-3 focus-within:ring-focus-glow focus-within:inset-ring-accent">
            <span aria-hidden="true" className="font-mono text-[15px] text-ink-3">
              @
            </span>
            <input
              id={id}
              value={form.value}
              onChange={(event) => {
                form.setValue(event.target.value)
              }}
              autoComplete="off"
              autoCapitalize="none"
              spellCheck={false}
              maxLength={INVITED_USERNAME_MAX_LENGTH}
              aria-invalid={form.formatInvalid}
              aria-describedby={form.formatInvalid ? errorId : undefined}
              className="h-full min-w-0 flex-1 bg-transparent font-mono text-[15px] tracking-[0.01em] text-ink outline-none"
            />
          </div>
        </div>
        <PrimaryButton disabled={form.locked || form.pending}>{t('submit')}</PrimaryButton>
      </div>
      <FieldError id={errorId} error={form.formatInvalid ? t('invalidUsername') : null} />
      <Outcome outcome={form.outcome} onAccept={onAccept} accepting={accepting} />
      <FormAlert failure={form.formFailure} />
      <p className="text-[13px] leading-[1.4] text-ink-3">{t('help')}</p>
    </form>
  )
}

interface OutcomeProps {
  readonly outcome: SendOutcome | null
  readonly onAccept: (requestId: string) => void
  readonly accepting: boolean
}

/** ADR 0038 (point 1) : succès en statut, refus en alerte, l'identifiant en mono. */
function Outcome({ outcome, onAccept, accepting }: OutcomeProps) {
  const t = useMessages(sendFriendRequestMessages)
  const username = outcome === null ? '' : `@${outcome.username}`
  if (outcome?.kind === 'sent' || outcome?.kind === 'incoming') {
    return (
      <div role="status">
        <Notice variant="success">
          <span className="flex flex-col gap-1.5">
            <span>
              {outcome.kind === 'sent'
                ? withUsername(t('sent', { username }), username)
                : withUsername(t('incoming', { username }), username)}
            </span>
            {outcome.kind === 'incoming' && (
              <button
                type="button"
                disabled={accepting}
                onClick={() => {
                  onAccept(outcome.requestId)
                }}
                className="self-start text-[13px] font-semibold text-accent-text underline underline-offset-[0.2em] hover:text-accent-hover disabled:opacity-60"
              >
                {t('accept')}
              </button>
            )}
          </span>
        </Notice>
      </div>
    )
  }
  if (outcome === null) {
    return null
  }
  const text = (() => {
    switch (outcome.kind) {
      case 'not-found':
        return withUsername(t('notFound', { username }), username)
      case 'self':
        return t('self')
      case 'already-friends':
        return t('alreadyFriends')
      case 'already-pending':
        return t('alreadyPending')
      case 'cannot-invite':
        return t('cannotInvite')
      case 'invalid-username':
        return t('invalidUsername')
    }
  })()
  return <Alert variant="error">{text}</Alert>
}

function withUsername(text: string, username: string) {
  return <UsernameInText text={text} username={username} />
}
