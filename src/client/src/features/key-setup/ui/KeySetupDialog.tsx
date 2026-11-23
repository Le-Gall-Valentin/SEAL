import { type ReactNode, type Ref, useEffect, useId, useRef } from 'react'
import type { KeyOwner } from '@/entities/keyring'
import { useMessages } from '@/shared/i18n'
import { Alert, Dialog, FailureAlert, FieldError, FieldLabel, SecondaryButton } from '@/shared/ui'
import { keySetupMessages } from '../i18n'
import { type KeySetup, type Slot, useKeySetup } from '../model/useKeySetup'

type KeySetupDialogProps = {
  readonly owner: KeyOwner
  /** Appelé une fois le parcours fini, ou repris par un autre onglet : la zone du compte relit l'état des clés. */
  readonly onDone: () => void
  /** Affiché tant qu'un autre onglet tient le verrou des clés (ADR 0029, point 13) ; rien sinon. */
  readonly waiting?: ReactNode
} & (
  | { readonly variant?: 'setup' }
  | {
      /** KEY-34, ADR 0036 : nouvelle identité qui remplace l'ancienne, en mode sudo. */
      readonly variant: 'reset'
      /** 403 REAUTH_REQUIRED : l'écran ouvre la ré-authentification, puis appelle retry (ADR 0035). */
      readonly onReauthRequired: (retry: () => void) => void
    }
)

const HEADER = 'flex flex-col gap-1 px-6 pt-6 pb-3'
const STEP = 'text-[12px] font-semibold tracking-[0.08em] text-ink-3 uppercase'
const TITLE = 'm-0 text-[20px] leading-[1.2] font-bold tracking-[-0.015em]'
const BODY = 'flex flex-col gap-4 px-6 pb-[22px]'
const TEXT = 'm-0 text-[14.5px] leading-[1.45] text-ink-2'
const FOOTER = 'flex justify-end gap-2 border-t border-line px-6 pt-4 pb-5'
/** Anneau de focus de la maquette (« Terminer ») : 2 px de --focus-ring, à 2 px du bouton. */
const FOCUS_RING =
  'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus-ring'
const PRIMARY = `h-11 rounded-[10px] bg-accent px-4 font-sans text-[14.5px] font-semibold text-on-accent disabled:opacity-55 ${FOCUS_RING}`

/**
 * KEY-02, KEY-30 : Dialogue-Cle-Recuperation, en deux étapes, sur le fond seul (ADR 0029) ; à la première connexion,
 * ou à la réinitialisation de l'identité (KEY-34, ADR 0036 point 3), avec les mêmes textes.
 */
export function KeySetupDialog(props: KeySetupDialogProps) {
  const { owner, onDone } = props
  const setup = useKeySetup(
    owner,
    onDone,
    props.variant === 'reset' ? { onReauthRequired: props.onReauthRequired } : undefined,
  )
  const titleId = useId()
  const textId = useId()
  if (!setup.ready) {
    return setup.waiting ? (props.waiting ?? null) : null
  }
  return (
    <Dialog labelledBy={titleId} describedBy={textId} stepKey={setup.step}>
      {setup.step === 1 ? (
        <ShowStep setup={setup} titleId={titleId} textId={textId} />
      ) : (
        <VerifyStep setup={setup} titleId={titleId} textId={textId} />
      )}
    </Dialog>
  )
}

interface StepProps {
  readonly setup: KeySetup
  readonly titleId: string
  readonly textId: string
}

function ShowStep({ setup, titleId, textId }: StepProps) {
  const t = useMessages(keySetupMessages)
  return (
    <>
      <header className={HEADER}>
        <span className={STEP}>{t('step', { current: 1 })}</span>
        <h2 id={titleId} className={TITLE}>
          {t('showTitle')}
        </h2>
      </header>
      <div className={BODY}>
        <p id={textId} className={TEXT}>
          {t('showText')}
        </p>
        <ol
          aria-label={t('groupsLabel')}
          // KEY-31 : la traduction automatique du navigateur enverrait la clé à un service tiers.
          translate="no"
          className="m-0 grid list-none grid-cols-4 gap-x-2.5 gap-y-3 rounded-[14px] bg-sunken px-[18px] py-4 inset-ring inset-ring-line"
        >
          {setup.groups.map((code, index) => (
            // Les groupes sont des positions fixes de la clé (KEY-30) : l'index est leur identité.
            <li key={index} className="flex flex-col items-center gap-0.5">
              <span className="font-mono text-[11px] text-ink-3">{index + 1}</span>
              <span className="font-mono text-[19px] font-medium tracking-[0.08em] text-ink">
                {code}
              </span>
            </li>
          ))}
        </ol>
        <div className="flex flex-wrap gap-2">
          <button
            type="button"
            onClick={setup.download}
            className={`inline-flex h-11 items-center gap-2 rounded-[10px] bg-surface pr-4 pl-3 font-sans text-[14.5px] font-semibold text-ink inset-ring inset-ring-line-strong ${FOCUS_RING}`}
          >
            <svg
              width="18"
              height="18"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="1.7"
              strokeLinecap="round"
              strokeLinejoin="round"
              aria-hidden="true"
            >
              <path d="M12 4v11M7.5 10.5L12 15l4.5-4.5M5 19.5h14" />
            </svg>
            {t('download')}
          </button>
        </div>
        <div
          role="note"
          className="flex gap-2.5 rounded-[14px] bg-warning-soft px-3.5 py-3 text-[13.5px] leading-[1.4] text-ink-2 inset-ring inset-ring-warning-line"
        >
          <svg
            width="18"
            height="18"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="1.7"
            strokeLinecap="round"
            strokeLinejoin="round"
            aria-hidden="true"
            className="mt-px flex-none text-warning"
          >
            <path d="M10.3 4.6a2 2 0 0 1 3.4 0l7.3 12.6a2 2 0 0 1-1.7 3H4.7a2 2 0 0 1-1.7-3z" />
            <path d="M12 9.5v4.2M12 16.8h.01" />
          </svg>
          <span>
            <strong className="text-warning">{t('onceTitle')}</strong> {t('onceText')}
          </span>
        </div>
      </div>
      <footer className={FOOTER}>
        <button type="button" onClick={setup.verify} className={PRIMARY}>
          {t('noted')}
        </button>
      </footer>
    </>
  )
}

function VerifyStep({ setup, titleId, textId }: StepProps) {
  const t = useMessages(keySetupMessages)
  const firstInput = useRef<HTMLInputElement>(null)
  useEffect(() => {
    firstInput.current?.focus()
  }, [])
  const slots: readonly Slot[] = [0, 1]
  return (
    <form
      className="contents"
      onSubmit={(event) => {
        event.preventDefault()
        setup.finish()
      }}
    >
      <header className={HEADER}>
        <span className={STEP}>{t('step', { current: 2 })}</span>
        <h2 id={titleId} className={TITLE}>
          {t('verifyTitle')}
        </h2>
      </header>
      <div className={BODY}>
        <p id={textId} className={TEXT}>
          {t('verifyText')}
        </p>
        <div className="grid grid-cols-2 gap-3">
          {slots.map((slot) => (
            <GroupInput
              key={slot}
              label={t('group', { number: setup.picked[slot] + 1 })}
              value={setup.typed[slot]}
              onChange={(value) => {
                setup.type(slot, value)
              }}
              error={setup.mismatch(slot) ? t('wrongGroup') : null}
              inputRef={slot === 0 ? firstInput : undefined}
            />
          ))}
        </div>
        <div role="status" className="empty:hidden">
          {setup.confirmed && (
            <div className="flex items-center gap-2.5 rounded-[14px] bg-accent-soft px-3.5 py-3 text-[14px] font-semibold text-accent-text inset-ring inset-ring-accent-line">
              <svg
                width="18"
                height="18"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="1.8"
                strokeLinecap="round"
                strokeLinejoin="round"
                aria-hidden="true"
                className="flex-none"
              >
                <path d="M12 3l7.5 3v5.5c0 4.6-3.2 8.3-7.5 9.5-4.3-1.2-7.5-4.9-7.5-9.5V6z" />
                <path d="M8.8 12.2l2.2 2.2 4.3-4.4" />
              </svg>
              {t('confirmed')}
            </div>
          )}
        </div>
        {setup.confirmed && setup.failure === null && (
          <p className="m-0 text-[13.5px] leading-[1.45] text-ink-3">{t('savedText')}</p>
        )}
        {setup.failure === 'network' && <Alert variant="error">{t('saveFailed')}</Alert>}
        {setup.failure === 'unexpected' && <FailureAlert failure={{ kind: 'unexpected' }} />}
      </div>
      <footer className={FOOTER}>
        <SecondaryButton onClick={setup.showKey}>{t('review')}</SecondaryButton>
        <button
          type="submit"
          disabled={!setup.confirmed || setup.saving}
          aria-busy={setup.saving}
          className={PRIMARY}
        >
          {t('finish')}
        </button>
      </footer>
    </form>
  )
}

interface GroupInputProps {
  readonly label: string
  readonly value: string
  readonly onChange: (value: string) => void
  readonly error: string | null
  readonly inputRef?: Ref<HTMLInputElement> | undefined
}

/**
 * Case d'un groupe ressaisi : 4 caractères, police monospace, centrés, en majuscules d'affichage comme
 * Dialogue-Cle-Recuperation ; la saisie reste telle quelle (ADR 0027, point 15).
 */
function GroupInput({ label, value, onChange, error, inputRef }: GroupInputProps) {
  const id = useId()
  const errorId = `${id}-erreur`
  const invalid = error !== null
  return (
    <div className="flex flex-col gap-1.5">
      <FieldLabel htmlFor={id}>{label}</FieldLabel>
      <input
        ref={inputRef}
        id={id}
        value={value}
        onChange={(event) => {
          onChange(event.target.value)
        }}
        maxLength={4}
        autoComplete="off"
        spellCheck={false}
        autoCapitalize="characters"
        translate="no"
        aria-invalid={invalid}
        aria-describedby={invalid ? errorId : undefined}
        className="h-12 w-full min-w-0 rounded-[10px] bg-surface text-center font-mono text-[18px] font-medium tracking-[0.1em] text-ink uppercase inset-ring inset-ring-line-strong outline-none focus:ring-3 focus:inset-ring-[1.5px] focus:ring-focus-glow focus:inset-ring-accent aria-invalid:inset-ring-danger"
      />
      <FieldError id={errorId} error={error} />
    </div>
  )
}
