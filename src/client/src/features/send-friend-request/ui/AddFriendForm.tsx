import { type Ref, useId, useState } from 'react'
import { useMessages } from '@/shared/i18n'
import type { FormFailure } from '@/shared/lib'
import { Alert, FieldError, FieldLabel, FormAlert, Notice } from '@/shared/ui'
import { sendFriendRequestMessages } from '../i18n'
import { INVITED_USERNAME_MAX_LENGTH } from '../model/invitedUsername'
import { type SendOutcome, useSendFriendRequest } from '../model/useSendFriendRequest'
import { PersonPlusIcon } from './icons'
import { UsernameInText } from './UsernameInText'

/** Issue de l'acceptation demandée à la page après un 409 INCOMING_REQUEST_EXISTS. */
export type IncomingAcceptResult =
  { readonly kind: 'accepted' } | { readonly kind: 'gone' } | FormFailure

interface AddFriendFormProps {
  /** FRD-01 : « Accepter l’invitation » après un 409 ; la page fournit l'acceptation et en rend l'issue. */
  readonly onAccept: (requestId: string) => Promise<IncomingAcceptResult>
  /** Appelé quand l'invitation reçue est acceptée depuis le formulaire (la feuille se ferme). */
  readonly onAccepted?: (() => void) | undefined
  /** Feuille du téléphone : champ et bouton sur toute la largeur (Amis-Telephone). */
  readonly variant?: 'aside' | 'sheet' | undefined
  /** Pour placer le focus sur le champ (feuille ouverte, « Ajouter un ami » de l'état vide). */
  readonly inputRef?: Ref<HTMLInputElement> | undefined
}

/** Formulaire « Ajouter un ami » de l'aside (Amis-Ordinateur) et de la feuille (Amis-Telephone), ADR 0038. */
export function AddFriendForm({
  onAccept,
  onAccepted,
  variant = 'aside',
  inputRef,
}: AddFriendFormProps) {
  const t = useMessages(sendFriendRequestMessages)
  const form = useSendFriendRequest()
  const id = useId()
  const errorId = `${id}-erreur`
  const [accepting, setAccepting] = useState(false)
  const [acceptIssue, setAcceptIssue] = useState<Exclude<
    IncomingAcceptResult,
    { kind: 'accepted' }
  > | null>(null)
  const accept = async (requestId: string) => {
    if (accepting) {
      return
    }
    setAccepting(true)
    setAcceptIssue(null)
    const result = await onAccept(requestId)
    setAccepting(false)
    if (result.kind === 'accepted') {
      form.clearOutcome()
      onAccepted?.()
    } else {
      form.clearOutcome()
      setAcceptIssue(result)
    }
  }
  return (
    <form
      noValidate
      className="flex flex-col gap-3.5"
      onSubmit={(event) => {
        event.preventDefault()
        setAcceptIssue(null)
        form.submit()
      }}
    >
      <div className={`flex flex-wrap items-end ${variant === 'sheet' ? 'gap-2.5' : 'gap-2'}`}>
        <div
          className={`flex min-w-0 flex-col gap-1.5 ${variant === 'sheet' ? 'basis-full' : 'flex-[1_1_192px]'}`}
        >
          <FieldLabel htmlFor={id}>{t('username')}</FieldLabel>
          <div className="flex h-11 items-center rounded-[10px] bg-surface px-3 inset-ring inset-ring-line-strong focus-within:ring-3 focus-within:ring-focus-glow focus-within:inset-ring-accent">
            <span aria-hidden="true" className="font-mono text-[15px] text-ink-3">
              @
            </span>
            <input
              ref={inputRef}
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
        <button
          type="submit"
          disabled={form.locked || form.pending}
          className={`inline-flex h-11 items-center justify-center gap-2 rounded-[10px] bg-accent pr-4 pl-3.5 text-[14.5px] font-semibold text-on-accent hover:bg-accent-hover disabled:opacity-55 ${variant === 'sheet' ? 'flex-1' : ''}`}
        >
          <PersonPlusIcon />
          {t('submit')}
        </button>
      </div>
      <FieldError id={errorId} error={form.formatInvalid ? t('invalidUsername') : null} />
      <Outcome
        outcome={form.outcome}
        onAccept={(requestId) => {
          void accept(requestId)
        }}
        accepting={accepting}
      />
      {acceptIssue?.kind === 'gone' && <Alert variant="error">{t('gone')}</Alert>}
      <FormAlert
        failure={
          acceptIssue !== null && acceptIssue.kind !== 'gone' ? acceptIssue : form.formFailure
        }
      />
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
  const success = outcome?.kind === 'sent' || outcome?.kind === 'incoming'
  const status = (
    // Région toujours présente, pour qu'un lecteur d'écran annonce le texte qui y paraît.
    <div role="status" className="empty:hidden">
      {success && (
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
      )}
    </div>
  )
  if (success || outcome === null) {
    return status
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
  return (
    <>
      {status}
      <Alert variant="error">{text}</Alert>
    </>
  )
}

function withUsername(text: string, username: string) {
  return <UsernameInText text={text} username={username} />
}
