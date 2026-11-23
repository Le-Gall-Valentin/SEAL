import { type ReactNode, useEffect, useId, useRef } from 'react'
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
  /** Identifiant du texte qui décrit la fenêtre. */
  readonly describedBy?: string | undefined
  readonly children: ReactNode
}

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
  describedBy,
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
      aria-labelledby={titleId}
      aria-describedby={describedBy}
      className="m-auto mt-10 w-[calc(100%-32px)] max-w-[500px] overflow-y-auto rounded-[18px] bg-raised p-0 font-sans text-ink shadow-dialog backdrop:bg-overlay"
      onCancel={(event) => {
        // Échap : la fenêtre ne se ferme que si l'écran le permet, et par lui.
        event.preventDefault()
        if (closable) {
          onClose()
        }
      }}
    >
      {open && (
        <>
          <div className="flex items-start gap-3 pt-5 pr-5 pb-3 pl-6">
            <div className="flex min-w-0 flex-1 flex-col gap-1 pt-1">
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
                <span className="text-[14px] font-semibold text-ink-3">{subtitle}</span>
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
        </>
      )}
    </dialog>
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
          className="h-11 rounded-[10px] bg-accent px-4 font-sans text-[14.5px] font-semibold whitespace-nowrap text-on-accent disabled:opacity-55"
        >
          {submitLabel}
        </button>
      )}
    </div>
  )
}
