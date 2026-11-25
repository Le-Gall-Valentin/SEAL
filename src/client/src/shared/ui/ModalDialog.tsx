import { createContext, type ReactNode, use, useEffect, useId, useRef } from 'react'
import { useMessages } from '@/shared/i18n'
import { formMessages } from './formMessages'

interface ModalDialogProps {
  readonly open: boolean
  /** « Fermer », « Annuler » ou Échap ; jamais appelé tant que closable est faux. */
  readonly onClose: () => void
  /** Faux pendant un envoi : la fenêtre ne se ferme pas avant la réponse (ADR 0035). */
  readonly closable: boolean
  readonly title: string
  /** Au-dessus du titre, en capitales : « Étape 1 sur 2 » (Dialogue-2FA). */
  readonly eyebrow?: string | undefined
  /** Sous le titre (Dialogue-Suppression). */
  readonly subtitle?: string | undefined
  /**
   * Action irréversible : cadre de Dialogue-Suppression (rôle alertdialog, triangle d'alerte, en-tête, sous-titre et
   * bouton d'envoi en --danger) ; cadre de Dialogue-2FA sinon, sous-titre en --ink-3 (ADR 0035, ADR 0036 point 3).
   */
  readonly danger?: boolean
  /** Identifiant du texte qui décrit la fenêtre. */
  readonly describedBy?: string | undefined
  /** 528 px au lieu de 500 : Nouvelle-Conversation, qui loge le sélecteur d'amis. */
  readonly wide?: boolean
  readonly children: ReactNode
}

/** Ton de la fenêtre, que son pied lit : un seul drapeau, porté par le cadre. */
const DangerContext = createContext(false)

/**
 * Fenêtre modale au-dessus d'un écran (Dialogue-Suppression, Dialogue-2FA ; ADR 0035, point 1) : élément `dialog`
 * natif, qui rend le reste de la page inerte, et rend le focus à l'élément d'origine à la fermeture. 500 px au plus,
 * rayon 18, fond --raised, ombre --shadow-3, voile --overlay. Plus haute que la fenêtre (téléphone en paysage), elle
 * défile : le navigateur borne sa hauteur.
 */
export function ModalDialog({
  open,
  onClose,
  closable,
  title,
  eyebrow,
  subtitle,
  danger = false,
  describedBy,
  wide = false,
  children,
}: ModalDialogProps) {
  const t = useMessages(formMessages)
  const ref = useRef<HTMLDialogElement>(null)
  const titleId = useId()
  useEffect(() => {
    const dialog = ref.current
    if (dialog === null) {
      return
    }
    if (open && !dialog.open) {
      dialog.showModal()
      // showModal place le focus sur « Fermer », premier élément focalisable : le premier champ le prend.
      dialog.querySelector<HTMLElement>('input:not([disabled]), textarea:not([disabled])')?.focus()
    } else if (!open && dialog.open) {
      dialog.close()
    }
  }, [open])
  return (
    <dialog
      ref={ref}
      role={danger ? 'alertdialog' : undefined}
      aria-labelledby={titleId}
      aria-describedby={describedBy}
      className={`m-auto mt-10 w-[calc(100%-32px)] overflow-y-auto rounded-[18px] bg-raised p-0 font-sans text-ink shadow-dialog backdrop:bg-overlay ${danger ? 'max-w-[520px]' : wide ? 'max-w-[528px]' : 'max-w-[500px]'}`}
      onCancel={(event) => {
        // Échap : la fenêtre ne se ferme que si l'écran le permet, et par lui.
        event.preventDefault()
        if (closable) {
          onClose()
        }
      }}
    >
      {open && (
        <DangerContext value={danger}>
          <div
            className={`flex items-start pr-5 pl-6 ${danger ? 'gap-3.5 pt-5.5 pb-3.5' : 'gap-3 pt-5 pb-3'}`}
          >
            {danger && <DangerIcon />}
            <div className={`flex min-w-0 flex-1 flex-col gap-1 ${danger ? 'pt-0.5' : 'pt-1'}`}>
              {eyebrow !== undefined && (
                <span className="text-[12px] font-semibold tracking-[0.08em] text-ink-3 uppercase">
                  {eyebrow}
                </span>
              )}
              <h2
                id={titleId}
                className="m-0 text-[20px] leading-[1.2] font-bold tracking-[-0.015em]"
              >
                {title}
              </h2>
              {subtitle !== undefined && (
                <span
                  className={`text-[14px] font-semibold ${danger ? 'text-danger' : 'text-ink-3'}`}
                >
                  {subtitle}
                </span>
              )}
            </div>
            <button
              type="button"
              aria-label={t('close')}
              disabled={!closable}
              className="grid size-11 flex-none place-items-center rounded-[10px] bg-transparent text-ink-2 disabled:opacity-55"
              onClick={onClose}
            >
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
                <path d="M6.5 6.5l11 11M17.5 6.5l-11 11" />
              </svg>
            </button>
          </div>
          {children}
        </DangerContext>
      )}
    </dialog>
  )
}

/**
 * Triangle d'alerte de Dialogue-Suppression : 44 px, --danger-soft, anneau --danger-line, pictogramme --danger. Décoratif,
 * il s'efface sous 360 px, où le titre n'aurait plus la place de tenir à côté de lui (ADR 0024, point 11).
 */
function DangerIcon() {
  return (
    <span
      aria-hidden="true"
      className="hidden size-11 flex-none place-items-center rounded-xl bg-danger-soft text-danger inset-ring inset-ring-danger-line min-[360px]:grid"
    >
      <svg
        width="22"
        height="22"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.8"
        strokeLinecap="round"
        strokeLinejoin="round"
      >
        <path d="M10.3 4.6a2 2 0 0 1 3.4 0l7.3 12.6a2 2 0 0 1-1.7 3H4.7a2 2 0 0 1-1.7-3z" />
        <path d="M12 9.5v4.2M12 16.8h.01" />
      </svg>
    </span>
  )
}

interface DialogBodyProps {
  readonly children: ReactNode
}

/** Contenu d'une fenêtre, entre l'en-tête et le pied. */
export function DialogBody({ children }: DialogBodyProps) {
  return <div className="flex flex-col gap-4 px-6 pb-5.5">{children}</div>
}

interface DialogFooterProps {
  readonly onCancel: () => void
  /** Faux pendant un envoi. */
  readonly cancelable: boolean
  /** Absent : pas de bouton d'envoi (ré-authentification par fournisseur seulement). */
  readonly submitLabel?: string | undefined
  readonly canSubmit?: boolean
}

/**
 * Pied d'une fenêtre : « Annuler » et le bouton d'envoi du formulaire qui l'entoure. Sur un écran trop étroit, le
 * bouton d'envoi passe à la ligne entier plutôt que de couper son texte (280 px, ADR 0024, point 11).
 */
export function DialogFooter({
  onCancel,
  cancelable,
  submitLabel,
  canSubmit = false,
}: DialogFooterProps) {
  const t = useMessages(formMessages)
  // Bouton d'envoi en --danger quand la fenêtre annonce une action irréversible (Dialogue-Suppression).
  const danger = use(DangerContext)
  return (
    <div className="flex flex-wrap justify-end gap-2 border-t border-line px-6 pt-4 pb-5">
      <button
        type="button"
        disabled={!cancelable}
        className="h-11 rounded-[10px] bg-surface px-4 font-sans text-[14.5px] font-semibold whitespace-nowrap text-ink inset-ring inset-ring-line-strong disabled:opacity-55"
        onClick={onCancel}
      >
        {t('cancel')}
      </button>
      {submitLabel !== undefined && (
        <button
          type="submit"
          disabled={!canSubmit}
          className={`h-11 rounded-[10px] px-4 font-sans text-[14.5px] font-semibold whitespace-nowrap disabled:opacity-55 ${danger ? 'bg-danger text-on-danger' : 'bg-accent text-on-accent'}`}
        >
          {submitLabel}
        </button>
      )}
    </div>
  )
}
